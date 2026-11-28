package com.service_demandes.service;

import com.service_demandes.client.ConsultationResultat;
import com.service_demandes.client.DonStockClient;
import com.service_demandes.client.DonStockIndisponibleException;
import com.service_demandes.client.ReservationCommande;
import com.service_demandes.client.ReservationResultat;
import com.service_demandes.client.StatutReservation;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.repository.DemandeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

/**
 * S6-10 — Job de réconciliation des demandes ouvertes.
 *
 * <p>S'exécute toutes les {@code RETRY_JOBS_INTERVAL_SEC} secondes (défaut 60 s).</p>
 *
 * <h3>Logique par statut</h3>
 * <table border="1">
 *   <tr><th>Statut</th><th>Action</th></tr>
 *   <tr><td>EN_ATTENTE</td>
 *       <td>Appelle {@code ConsulterReservation}.
 *           Si NOT_FOUND (Don & Stock n'a jamais vu la demande) : rejoue {@code VerifierEtReserver}
 *           avec le même {@code demandeId} (idempotent, R18).
 *           Si LIVREE : passe à SATISFAITE.
 *           Sinon applique le statut courant.</td></tr>
 *   <tr><td>APPROVISIONNEMENT_EN_COURS</td>
 *       <td>Appelle {@code ConsulterReservation}.
 *           Si LIVREE : passe à SATISFAITE et ferme les appels ouverts pour ce groupe/composant.</td></tr>
 * </table>
 *
 * <p>Si Don & Stock est toujours indisponible, la demande reste dans son statut ouvert
 * et le job réessaiera à la prochaine exécution.</p>
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final DemandeRepository demandes;
    private final DonStockClient donStock;
    private final DemandeService demandeService;
    private final AppelDonService appelDonService;
    private final TransactionTemplate transaction;

    public ReconciliationJob(DemandeRepository demandes,
                             DonStockClient donStock,
                             DemandeService demandeService,
                             AppelDonService appelDonService,
                             TransactionTemplate transaction) {
        this.demandes = demandes;
        this.donStock = donStock;
        this.demandeService = demandeService;
        this.appelDonService = appelDonService;
        this.transaction = transaction;
    }

    /**
     * Exécuté à intervalle fixe défini par {@code demandes.retry-jobs-interval-sec}.
     * {@code fixedDelayString} relit la propriété à chaque démarrage du contexte Spring ;
     * la valeur en ms est {@code RETRY_JOBS_INTERVAL_SEC * 1000}.
     */
    @Scheduled(fixedDelayString = "#{${demandes.retry-jobs-interval-sec:60} * 1000L}")
    public void reconcilier() {
        List<Demande> aReconcilier = demandes.findByStatutIn(
                List.of(StatutDemande.EN_ATTENTE, StatutDemande.APPROVISIONNEMENT_EN_COURS));

        if (aReconcilier.isEmpty()) {
            return;
        }

        log.info("Réconciliation : {} demande(s) à traiter", aReconcilier.size());

        for (Demande demande : aReconcilier) {
            // Ignorer les demandes dont la réservation gRPC est actuellement en vol
            if (demandeService.getReservationsEnCours().contains(demande.getId())) {
                log.debug("Demande {} en cours de réservation — ignorée", demande.getId());
                continue;
            }
            try {
                traiterUne(demande);
            } catch (Exception e) {
                // On ne laisse pas une erreur sur une demande bloquer les suivantes
                log.warn("Erreur lors de la réconciliation de la demande {} : {}", demande.getId(), e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Traitement d'une demande
    // -------------------------------------------------------------------------

    private void traiterUne(Demande demande) {
        Optional<ConsultationResultat> optResultat;
        try {
            optResultat = donStock.consulterReservation(demande.getId());
        } catch (DonStockIndisponibleException e) {
            log.debug("Don & Stock indisponible pour demande {} — réessai plus tard", demande.getId());
            return;
        }

        if (optResultat.isEmpty()) {
            // NOT_FOUND : Don & Stock n'a jamais enregistré cette réservation
            // → rejouer VerifierEtReserver (R18 : idempotent par demandeId)
            rejouerVerification(demande);
            return;
        }

        ConsultationResultat consultation = optResultat.get();
        StatutReservation statutGrpc = consultation.statut();

        log.debug("Réconciliation demande {} : statut gRPC={}", demande.getId(), statutGrpc);

        switch (statutGrpc) {
            case LIVREE -> passerEnSatisfaite(demande);
            case RESERVEE_STOCK_LOCAL -> passerEnSatisfaite(demande);
            case APPROVISIONNEMENT_COMMANDE, EN_ATTENTE_CONFIRMATION -> {
                // Toujours en transit : mettre à jour la référence si elle manque
                if (demande.getReferenceApprovisionnement() == null
                        && consultation.reference() != null) {
                    transaction.execute(tx -> {
                        demandes.findByIdVerrouillee(demande.getId()).ifPresent(d -> {
                            d.setStatut(StatutDemande.APPROVISIONNEMENT_EN_COURS);
                            d.setReferenceApprovisionnement(consultation.reference());
                            demandes.save(d);
                        });
                        return null;
                    });
                }
            }
            case STOCK_INSUFFISANT_PARTOUT -> {
                // Toujours en pénurie : s'assurer que le statut local est EN_PENURIE
                if (demande.getStatut() != StatutDemande.EN_PENURIE) {
                    transaction.execute(tx -> {
                        demandes.findByIdVerrouillee(demande.getId()).ifPresent(d -> {
                            d.setStatut(StatutDemande.EN_PENURIE);
                            demandes.save(d);
                            appelDonService.ouvrirOuReutiliser(d);
                        });
                        return null;
                    });
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Rejouer VerifierEtReserver (NOT_FOUND dans ConsulterReservation)
    // -------------------------------------------------------------------------

    private void rejouerVerification(Demande demande) {
        log.info("Demande {} absente de Don & Stock — rejeu de VerifierEtReserver", demande.getId());
        ReservationResultat resultat;
        try {
            resultat = donStock.verifierEtReserver(new ReservationCommande(
                    demande.getId(),
                    demande.getHopitalId(),
                    demande.getGroupeSanguin(),
                    demande.getComposant(),
                    demande.getQuantitePoches(),
                    demande.getUrgence()));
        } catch (DonStockIndisponibleException e) {
            log.debug("Don & Stock indisponible lors du rejeu de la demande {} — réessai plus tard",
                    demande.getId());
            return;
        }

        transaction.execute(tx -> {
            demandeService.appliquerResultat(demande.getId(), resultat);
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Passer une demande en SATISFAITE
    // -------------------------------------------------------------------------

    private void passerEnSatisfaite(Demande demande) {
        if (demande.getStatut() == StatutDemande.SATISFAITE) {
            return; // idempotent
        }

        transaction.execute(tx -> {
            demandes.findByIdVerrouillee(demande.getId()).ifPresent(d -> {
                if (d.getStatut() == StatutDemande.ANNULEE) {
                    log.info("Demande {} annulée — transition SATISFAITE ignorée", d.getId());
                    return;
                }
                d.setStatut(StatutDemande.SATISFAITE);
                demandes.save(d);
                log.info("Demande {} passée en SATISFAITE par le job de réconciliation", d.getId());

                // Fermer les appels publics ouverts pour ce groupe + composant si le stock est revenu
                appelDonService.fermerAutomatiquement(d.getGroupeSanguin(), d.getComposant());
            });
            return null;
        });
    }
}
