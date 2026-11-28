package com.service_demandes.service;

import com.service_demandes.client.DonStockClient;
import com.service_demandes.client.DonStockIndisponibleException;
import com.service_demandes.client.ReservationCommande;
import com.service_demandes.client.ReservationResultat;
import com.service_demandes.client.StatutReservation;
import com.service_demandes.config.DemandesRulesProperties;
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
    private final AppelDonService appelDonService;
    private final TransactionTemplate transaction;
    private final DemandesRulesProperties rules;

    /**
     * Garde en mémoire les demandes dont la réservation gRPC est en vol.
     * Permet de bloquer une annulation concurrente jusqu'à la fin de l'appel.
     */
    private final Set<UUID> reservationsEnCours = ConcurrentHashMap.newKeySet();

    public DemandeService(DemandeRepository demandes,
                          DonStockClient donStock,
                          AppelDonService appelDonService,
                          TransactionTemplate transaction,
                          DemandesRulesProperties rules) {
        this.demandes = demandes;
        this.donStock = donStock;
        this.appelDonService = appelDonService;
        this.transaction = transaction;
        this.rules = rules;
    }

    // -------------------------------------------------------------------------
    // S6-05 : Création d'une demande
    // -------------------------------------------------------------------------

    /**
     * Crée une demande et tente immédiatement la réservation gRPC.
     * <p>Deux transactions : (1) sauvegarde EN_ATTENTE, (2) applique le résultat.
     * Aucune transaction n'est ouverte pendant l'appel réseau.</p>
     */
    public Demande creer(CreateDemandeRequest requete) {
        validerCompensation(requete.compensationDemandee());

        // --- Transaction 1 : la demande est commitée avant tout appel réseau ---
        Demande enAttente = transaction.execute(tx -> demandes.save(Demande.builder()
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

        // --- Appel gRPC hors transaction ---
        reservationsEnCours.add(enAttente.getId());
        ReservationResultat resultat;
        try {
            resultat = donStock.verifierEtReserver(new ReservationCommande(
                    enAttente.getId(),
                    enAttente.getHopitalId(),
                    enAttente.getGroupeSanguin(),
                    enAttente.getComposant(),
                    enAttente.getQuantitePoches(),
                    enAttente.getUrgence()));
        } catch (DonStockIndisponibleException e) {
            log.warn("Don & Stock indisponible pour demande {} : {} — laissée EN_ATTENTE",
                    enAttente.getId(), e.getMessage());
            return enAttente;
        } finally {
            reservationsEnCours.remove(enAttente.getId());
        }

        // --- Transaction 2 : mise à jour du statut ---
        return transaction.execute(tx -> appliquerResultat(enAttente.getId(), resultat));
    }

    // -------------------------------------------------------------------------
    // S6-06 : Applique le résultat gRPC → mapping statut + ouverture appel (S6-07)
    // -------------------------------------------------------------------------

    /**
     * Applique le résultat d'un {@code VerifierEtReserver} sur la demande.
     * Doit être appelé dans une transaction active.
     */
    Demande appliquerResultat(UUID id, ReservationResultat resultat) {
        Demande demande = demandes.findByIdVerrouillee(id)
                .orElseThrow(() -> new IllegalStateException("Demande disparue : " + id));

        // Si la demande a été annulée pendant l'appel gRPC
        if (demande.getStatut() == StatutDemande.ANNULEE) {
            if (resultat.statut() == StatutReservation.RESERVEE_STOCK_LOCAL) {
                try {
                    donStock.libererReservation(id, resultat.reference());
                } catch (RuntimeException e) {
                    log.warn("Impossible de libérer la réservation {} de la demande annulée {}", resultat.reference(), id, e);
                }
            }
            log.info("Demande {} annulée pendant la réservation — résultat ignoré", id);
            return demande;
        }

        // Pas de réapplication si le statut a déjà évolué (idempotence)
        if (demande.getStatut() != StatutDemande.EN_ATTENTE) {
            log.info("Demande {} déjà au statut {} — résultat gRPC ignoré", id, demande.getStatut());
            return demande;
        }

        // --- S6-06 : tableau de mapping (étude section 7.3) ---
        switch (resultat.statut()) {
            case RESERVEE_STOCK_LOCAL -> {
                demande.setStatut(StatutDemande.SATISFAITE);
                demande.setReferenceApprovisionnement(resultat.reference());
            }
            case APPROVISIONNEMENT_COMMANDE, EN_ATTENTE_CONFIRMATION -> {
                demande.setStatut(StatutDemande.APPROVISIONNEMENT_EN_COURS);
                demande.setReferenceApprovisionnement(resultat.reference());
            }
            case STOCK_INSUFFISANT_PARTOUT -> {
                demande.setStatut(StatutDemande.EN_PENURIE);
                // --- S6-07 : ouvrir ou réutiliser un appel public pour ce groupe + composant ---
                appelDonService.ouvrirOuReutiliser(demande);
                log.info("Demande {} EN_PENURIE — appel public ouvert pour {} / {}",
                        id, demande.getGroupeSanguin(), demande.getComposant());
            }
            case LIVREE -> {
                // Peut arriver si le job de réconciliation appelle appliquerResultat sur un résultat LIVREE
                demande.setStatut(StatutDemande.SATISFAITE);
            }
        }

        return demandes.save(demande);
    }

    // -------------------------------------------------------------------------
    // Lecture et modification
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Demande> lister(StatutDemande statut, String hopitalId) {
        Specification<Demande> filtre = (r, q, cb) -> cb.conjunction();
        if (statut != null) {
            filtre = filtre.and((r, q, cb) -> cb.equal(r.get("statut"), statut));
        }
        if (hopitalId != null && !hopitalId.isBlank()) {
            filtre = filtre.and((r, q, cb) -> cb.equal(r.get("hopitalId"), hopitalId));
        }
        return demandes.findAll(filtre, Sort.sort(Demande.class).by(Demande::getDateCreation).descending());
    }

    @Transactional(readOnly = true)
    public Demande lire(UUID id) {
        return demandes.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Demande introuvable : " + id));
    }

    /**
     * Annule une demande ouverte et/ou corrige le compteur de compensation (repli manuel).
     * Seul le statut {@code ANNULEE} est accepté comme changement de statut.
     */
    @Transactional
    public Demande modifier(UUID id, ModifierDemandeRequest requete) {
        if (requete.statut() == null && requete.compensationRecue() == null) {
            throw new RequeteInvalideException("Au moins un champ est obligatoire : statut ou compensationRecue");
        }
        if (requete.statut() != null && requete.statut() != StatutDemande.ANNULEE) {
            throw new RequeteInvalideException("Seul le statut ANNULEE est accepté pour une mise à jour manuelle");
        }

        Demande demande = demandes.findByIdVerrouillee(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Demande introuvable : " + id));

        if (requete.statut() == StatutDemande.ANNULEE) {
            if (reservationsEnCours.contains(id)) {
                throw new ConflitMetierException(
                        "La demande est en cours de réservation — réessayez dans quelques secondes");
            }
            if (!demande.getStatut().estOuverte()) {
                throw new ConflitMetierException(
                        "La demande est au statut " + demande.getStatut() + " et ne peut plus être annulée");
            }
            demande.setStatut(StatutDemande.ANNULEE);
        }

        if (requete.compensationRecue() != null) {
            if (requete.compensationRecue() < 0) {
                throw new RequeteInvalideException("compensationRecue ne peut pas être négative");
            }
            demande.setCompensationRecue(requete.compensationRecue());
        }

        return demandes.save(demande);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void validerCompensation(Integer compensationDemandee) {
        if (compensationDemandee != null
                && (compensationDemandee < rules.getCompensationMin()
                    || compensationDemandee > rules.getCompensationMax())) {
            throw new RequeteInvalideException(
                    "compensationDemandee doit être entre %d et %d (R17)"
                            .formatted(rules.getCompensationMin(), rules.getCompensationMax()));
        }
    }

    /** Exposé pour le job de réconciliation (S6-10). */
    Set<UUID> getReservationsEnCours() {
        return reservationsEnCours;
    }
}
