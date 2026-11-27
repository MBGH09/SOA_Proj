package com.service_demandes.service;

import com.service_demandes.client.DonStockClient;
import com.service_demandes.config.DemandesRulesProperties;
import com.service_demandes.client.DonStockIndisponibleException;
import com.service_demandes.client.ReservationCommande;
import com.service_demandes.client.ReservationResultat;
import com.service_demandes.dto.CreateDemandeRequest;
import com.service_demandes.dto.ModifierDemandeRequest;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.exception.ConflitMetierException;
import com.service_demandes.exception.RequeteInvalideException;
import com.service_demandes.exception.RessourceIntrouvableException;
import com.service_demandes.repository.DemandeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DemandeService {

    private static final Logger log = LoggerFactory.getLogger(DemandeService.class);

    private final DemandeRepository demandes;
    private final DonStockClient donStock;
    private final TransactionTemplate transaction;
    private final DemandesRulesProperties rules;
    private final Set<UUID> reservationsEnCours = ConcurrentHashMap.newKeySet();

    public DemandeService(DemandeRepository demandes, DonStockClient donStock, TransactionTemplate transaction,
                          DemandesRulesProperties rules) {
        this.demandes = demandes;
        this.donStock = donStock;
        this.transaction = transaction;
        this.rules = rules;
    }

    public Demande creer(CreateDemandeRequest requete) {
        Integer compensation = requete.compensationDemandee();
        if (compensation != null
                && (compensation < rules.getCompensationMin() || compensation > rules.getCompensationMax())) {
            throw new RequeteInvalideException("La compensation demandée doit être entre "
                    + rules.getCompensationMin() + " et " + rules.getCompensationMax());
        }
        // 1re transaction : la demande existe en base avant tout appel reseau
        Demande enAttente = transaction.execute(status -> demandes.save(Demande.builder()
                .hopitalId(requete.hopitalId())
                .hopitalNom(requete.hopitalNom())
                .patientRef(requete.patientRef())
                .motif(requete.motif())
                .groupeSanguin(requete.groupeSanguin())
                .composant(requete.composant())
                .quantitePoches(requete.quantitePoches())
                .urgence(requete.urgence())
                .statut(StatutDemande.EN_ATTENTE)
                .compensationDemandee(requete.compensationDemandee())
                .build()));

        // Appel gRPC : pas de transaction ouverte ici
        ReservationResultat resultat;
        reservationsEnCours.add(enAttente.getId());
        try {
            resultat = donStock.verifierEtReserver(new ReservationCommande(
                    enAttente.getId(),
                    enAttente.getHopitalId(),
                    enAttente.getGroupeSanguin(),
                    enAttente.getComposant(),
                    enAttente.getQuantitePoches(),
                    enAttente.getUrgence()));
        } catch (DonStockIndisponibleException e) {
            // Repli : la demande reste EN_ATTENTE, la reconciliation (S6-10) la terminera
            log.warn("Don & Stock indisponible pour la demande {} : {}", enAttente.getId(), e.getMessage());
            return enAttente;
        } finally {
            reservationsEnCours.remove(enAttente.getId());
        }

        // 2e transaction : nouveau statut
        return transaction.execute(status -> appliquer(enAttente.getId(), resultat));
    }

    private Demande appliquer(UUID id, ReservationResultat resultat) {
        Demande demande = demandes.findByIdVerrouillee(id)
                .orElseThrow(() -> new IllegalStateException("Demande disparue pendant le traitement : " + id));

        if (demande.getStatut() != StatutDemande.EN_ATTENTE) {
            // annulee entre-temps : on ne l'ecrase pas
            if (demande.getStatut() == StatutDemande.ANNULEE
                    && resultat.statut() == com.service_demandes.client.StatutReservation.RESERVEE_STOCK_LOCAL) {
                try {
                    donStock.libererReservation(id, resultat.reference());
                } catch (RuntimeException e) {
                    log.warn("Impossible de liberer la reservation {} de la demande annulee {}",
                            resultat.reference(), id, e);
                }
            }
            log.info("Demande {} deja au statut {}, reponse de Don & Stock ignoree", id, demande.getStatut());
            return demande;
        }

        switch (resultat.statut()) {
            case RESERVEE_STOCK_LOCAL -> {
                demande.setStatut(StatutDemande.SATISFAITE);
                demande.setReferenceApprovisionnement(resultat.reference());
            }
            case APPROVISIONNEMENT_COMMANDE -> {
                demande.setStatut(StatutDemande.APPROVISIONNEMENT_EN_COURS);
                demande.setReferenceApprovisionnement(resultat.reference());
            }
            case STOCK_INSUFFISANT_PARTOUT -> {
                demande.setStatut(StatutDemande.EN_PENURIE);
                // S6-07 : ouvrir (ou reutiliser) l'appel public pour ce groupe et ce composant
            }
        }
        return demandes.save(demande);
    }

    /** Liste, de la plus recente a la plus ancienne. Les deux filtres sont facultatifs. */
    @Transactional(readOnly = true)
    public List<Demande> lister(StatutDemande statut, String hopitalId) {
        Specification<Demande> filtre = (racine, requete, cb) -> cb.conjunction();
        if (statut != null) {
            filtre = filtre.and((racine, requete, cb) -> cb.equal(racine.get("statut"), statut));
        }
        if (hopitalId != null && !hopitalId.isBlank()) {
            filtre = filtre.and((racine, requete, cb) -> cb.equal(racine.get("hopitalId"), hopitalId));
        }
        return demandes.findAll(filtre, Sort.sort(Demande.class).by(Demande::getDateCreation).descending());
    }

    @Transactional(readOnly = true)
    public Demande lire(UUID id) {
        return demandes.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Demande introuvable"));
    }

    /** Annule une demande ouverte et/ou corrige le compteur de compensation a la main (repli). */
    @Transactional
    public Demande modifier(UUID id, ModifierDemandeRequest requete) {
        if (requete.statut() == null && requete.compensationRecue() == null) {
            throw new RequeteInvalideException("Au moins un champ est obligatoire : statut ou compensationRecue");
        }
        if (requete.statut() != null && requete.statut() != StatutDemande.ANNULEE) {
            throw new RequeteInvalideException("Seul le statut ANNULEE est accepté");
        }

        Demande demande = demandes.findByIdVerrouillee(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Demande introuvable"));

        if (requete.statut() == StatutDemande.ANNULEE) {
            if (reservationsEnCours.contains(id)) {
                throw new ConflitMetierException("La demande est en cours de reservation et ne peut pas encore être annulée");
            }
            if (!demande.getStatut().estOuverte()) {
                throw new ConflitMetierException(
                        "La demande est au statut " + demande.getStatut() + " et ne peut plus être annulée");
            }
            demande.setStatut(StatutDemande.ANNULEE);
        }
        if (requete.compensationRecue() != null) {
            demande.setCompensationRecue(requete.compensationRecue());
        }
        return demandes.save(demande);
    }
}
