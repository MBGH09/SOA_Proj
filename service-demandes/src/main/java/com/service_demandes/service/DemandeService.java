package com.service_demandes.service;

import com.service_demandes.client.DonStockClient;
import com.service_demandes.client.DonStockIndisponibleException;
import com.service_demandes.client.ReservationCommande;
import com.service_demandes.client.ReservationResultat;
import com.service_demandes.dto.CreateDemandeRequest;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.repository.DemandeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Creation d'une demande (S6-05). Cette classe n'est volontairement PAS @Transactional :
 * <ol>
 *   <li>transaction 1 : enregistrer la demande EN_ATTENTE et valider (commit) ;</li>
 *   <li>appel de Don & Stock, sans aucune transaction ouverte (aucune connexion de base retenue pendant l'appel) ;</li>
 *   <li>transaction 2 : enregistrer le nouveau statut et la reference.</li>
 * </ol>
 */
@Service
public class DemandeService {

    private static final Logger log = LoggerFactory.getLogger(DemandeService.class);

    private final DemandeRepository demandes;
    private final DonStockClient donStock;
    private final TransactionTemplate transaction;

    public DemandeService(DemandeRepository demandes, DonStockClient donStock, TransactionTemplate transaction) {
        this.demandes = demandes;
        this.donStock = donStock;
        this.transaction = transaction;
    }

    public Demande creer(CreateDemandeRequest requete) {
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
        }

        // 2e transaction : nouveau statut
        return transaction.execute(status -> appliquer(enAttente.getId(), resultat));
    }

    private Demande appliquer(UUID id, ReservationResultat resultat) {
        Demande demande = demandes.findById(id)
                .orElseThrow(() -> new IllegalStateException("Demande disparue pendant le traitement : " + id));

        if (demande.getStatut() != StatutDemande.EN_ATTENTE) {
            // annulee entre-temps : on ne l'ecrase pas
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
}
