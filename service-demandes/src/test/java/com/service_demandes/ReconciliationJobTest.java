package com.service_demandes;

import com.service_demandes.client.ConsultationResultat;
import com.service_demandes.client.DonneursClient;
import com.service_demandes.client.DonStockIndisponibleException;
import com.service_demandes.client.ReservationResultat;
import com.service_demandes.client.StatutReservation;
import com.service_demandes.entity.AppelDon;
import com.service_demandes.entity.Composant;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.MotifDemande;
import com.service_demandes.entity.StatutAppelDon;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.entity.Urgence;
import com.service_demandes.repository.AppelDonRepository;
import com.service_demandes.repository.DemandeRepository;
import com.service_demandes.service.ReconciliationJob;
import com.service_demandes.support.DonStockClientAvecDisjoncteur;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration du {@link ReconciliationJob}.
 *
 * <p>Le scheduler automatique est désactivé dans {@code application-test.properties}.
 * On appelle {@link ReconciliationJob#reconcilier()} manuellement pour contrôler
 * exactement quand le job s'exécute.</p>
 *
 * <p>Couverture :</p>
 * <ul>
 *   <li>NOT_FOUND dans ConsulterReservation → rejoue VerifierEtReserver → SATISFAITE</li>
 *   <li>NOT_FOUND + Don & Stock toujours down → demande reste EN_ATTENTE</li>
 *   <li>LIVREE → demande passe SATISFAITE + appels ouverts fermés</li>
 *   <li>APPROVISIONNEMENT_EN_COURS → référence mise à jour dans la demande</li>
 *   <li>STOCK_INSUFFISANT → demande passe EN_PENURIE + appel public ouvert</li>
 *   <li>Demande annulée entre-temps → ignorée par le job</li>
 *   <li>Don & Stock injoignable pour ConsulterReservation → demande reste en l'état</li>
 *   <li>Aucune demande ouverte → le job s'exécute sans erreur</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReconciliationJobTest {

    @Autowired ReconciliationJob reconciliationJob;
    @Autowired DemandeRepository demandeRepository;
    @Autowired AppelDonRepository appelDonRepository;
    @Autowired DonStockClientAvecDisjoncteur clientDonStock;
    @Autowired CircuitBreakerRegistry registreDisjoncteurs;

    @MockitoBean DonneursClient donneursClient;

    // -------------------------------------------------------------------------
    // Remise à zéro du disjoncteur avant chaque test
    // -------------------------------------------------------------------------

    @BeforeEach
    void reinitialiserDisjoncteur() {
        registreDisjoncteurs.circuitBreaker("donStock").reset();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Demande persisterDemande(String hopitalId, StatutDemande statut, GroupeSanguin groupe) {
        return demandeRepository.save(Demande.builder()
                .hopitalId(hopitalId)
                .hopitalNom("Hôpital test")
                .patientRef("PAT-JOB")
                .motif(MotifDemande.CHIRURGIE)
                .groupeSanguin(groupe)
                .composant(Composant.CGR)
                .quantitePoches(2)
                .urgence(Urgence.HAUTE)
                .statut(statut)
                .dateCreation(Instant.now())
                .build());
    }

    private Demande persisterDemande(String hopitalId, StatutDemande statut) {
        return persisterDemande(hopitalId, statut, GroupeSanguin.O_NEG);
    }

    private ConsultationResultat consultation(StatutReservation statut, String reference) {
        return new ConsultationResultat("ignored", statut, reference, null, null);
    }

    // -------------------------------------------------------------------------
    // NOT_FOUND → rejoue VerifierEtReserver
    // -------------------------------------------------------------------------

    @Test
    void notFound_rejoueVerifierEtReserver_et_passe_en_SATISFAITE_si_stock_local_ok() {
        Demande demande = persisterDemande("H-REC-NOT-FOUND-OK", StatutDemande.EN_ATTENTE);

        // ConsulterReservation répond NOT_FOUND (Optional.empty)
        clientDonStock.configurerConsultation(Optional::empty);
        // VerifierEtReserver réussit avec stock local
        clientDonStock.configurerComportement(() ->
                new ReservationResultat(StatutReservation.RESERVEE_STOCK_LOCAL, "RES-JOB-1", null, null));

        reconciliationJob.reconcilier();

        Demande apres = demandeRepository.findById(demande.getId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutDemande.SATISFAITE);
        assertThat(apres.getReferenceApprovisionnement()).isEqualTo("RES-JOB-1");
    }

    @Test
    void notFound_rejoueVerifierEtReserver_et_passe_en_APPROVISIONNEMENT_si_commandeCentrale() {
        Demande demande = persisterDemande("H-REC-NOT-FOUND-CMD", StatutDemande.EN_ATTENTE);

        clientDonStock.configurerConsultation(Optional::empty);
        clientDonStock.configurerComportement(() ->
                new ReservationResultat(StatutReservation.APPROVISIONNEMENT_COMMANDE, "APP-JOB-1", "CRTS Sfax", 90));

        reconciliationJob.reconcilier();

        Demande apres = demandeRepository.findById(demande.getId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutDemande.APPROVISIONNEMENT_EN_COURS);
        assertThat(apres.getReferenceApprovisionnement()).isEqualTo("APP-JOB-1");
    }

    @Test
    void notFound_et_donStockToujrsDown_demande_reste_EN_ATTENTE() {
        Demande demande = persisterDemande("H-REC-NOT-FOUND-DOWN", StatutDemande.EN_ATTENTE);

        // ConsulterReservation répond NOT_FOUND
        clientDonStock.configurerConsultation(Optional::empty);
        // VerifierEtReserver échoue aussi
        clientDonStock.configurerComportement(() -> {
            throw new DonStockIndisponibleException("toujours en panne");
        });

        reconciliationJob.reconcilier();

        Demande apres = demandeRepository.findById(demande.getId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE);
    }

    // -------------------------------------------------------------------------
    // LIVREE → SATISFAITE + fermeture des appels ouverts
    // -------------------------------------------------------------------------

    @Test
    void livree_passe_demande_SATISFAITE_et_ferme_les_appels_ouverts() {
        GroupeSanguin groupe = GroupeSanguin.B_NEG;
        Demande demande = persisterDemande("H-REC-LIVREE", StatutDemande.APPROVISIONNEMENT_EN_COURS, groupe);

        // Créer un appel public ouvert pour ce groupe + composant
        AppelDon appelOuvert = appelDonRepository.save(AppelDon.builder()
                .groupeSanguin(groupe)
                .composant(Composant.CGR)
                .statut(StatutAppelDon.OUVERT)
                .build());

        clientDonStock.configurerConsultation(() ->
                Optional.of(consultation(StatutReservation.LIVREE, "REF-LIVREE")));

        reconciliationJob.reconcilier();

        Demande apres = demandeRepository.findById(demande.getId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutDemande.SATISFAITE);

        AppelDon appelApres = appelDonRepository.findById(appelOuvert.getId()).orElseThrow();
        assertThat(appelApres.getStatut()).isEqualTo(StatutAppelDon.FERME);
        assertThat(appelApres.getDateFermeture()).isNotNull();
    }

    @Test
    void livree_est_idempotent_si_demande_deja_SATISFAITE() {
        Demande demande = persisterDemande("H-REC-LIVREE-IDEM", StatutDemande.SATISFAITE);

        clientDonStock.configurerConsultation(() ->
                Optional.of(consultation(StatutReservation.LIVREE, "REF-IDEM")));

        // Ne doit pas lever d'exception
        reconciliationJob.reconcilier();

        Demande apres = demandeRepository.findById(demande.getId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutDemande.SATISFAITE);
    }

    // -------------------------------------------------------------------------
    // APPROVISIONNEMENT_COMMANDE → référence mise à jour
    // -------------------------------------------------------------------------

    @Test
    void approvisionnementCommande_metAJour_laReference_si_elle_manque() {
        Demande demande = persisterDemande("H-REC-CMD-REF", StatutDemande.EN_ATTENTE);
        // Aucune référence persistée encore

        clientDonStock.configurerConsultation(() ->
                Optional.of(consultation(StatutReservation.APPROVISIONNEMENT_COMMANDE, "REF-CENTRAL-1")));

        reconciliationJob.reconcilier();

        Demande apres = demandeRepository.findById(demande.getId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutDemande.APPROVISIONNEMENT_EN_COURS);
        assertThat(apres.getReferenceApprovisionnement()).isEqualTo("REF-CENTRAL-1");
    }

    // -------------------------------------------------------------------------
    // STOCK_INSUFFISANT → EN_PENURIE + appel public ouvert
    // -------------------------------------------------------------------------

    @Test
    void stockInsuffisant_passe_EN_PENURIE_et_ouvre_un_appel_public() {
        GroupeSanguin groupe = GroupeSanguin.AB_POS;
        Demande demande = persisterDemande("H-REC-PENURIE", StatutDemande.EN_ATTENTE, groupe);

        clientDonStock.configurerConsultation(() ->
                Optional.of(consultation(StatutReservation.STOCK_INSUFFISANT_PARTOUT, null)));

        reconciliationJob.reconcilier();

        Demande apres = demandeRepository.findById(demande.getId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutDemande.EN_PENURIE);

        // Un appel public doit être ouvert pour ce groupe + composant
        boolean appelOuvert = appelDonRepository.findByStatut(StatutAppelDon.OUVERT).stream()
                .anyMatch(a -> a.getGroupeSanguin() == groupe && a.getComposant() == Composant.CGR);
        assertThat(appelOuvert).isTrue();
    }

    // -------------------------------------------------------------------------
    // Demande annulée entre-temps → ignorée
    // -------------------------------------------------------------------------

    @Test
    void demande_annulee_entre_deux_executions_nest_pas_modifiee_par_le_job() {
        // La demande est d'abord EN_ATTENTE pour être dans la liste du job,
        // puis annulée juste avant l'exécution
        Demande demande = persisterDemande("H-REC-ANNULEE", StatutDemande.ANNULEE);

        clientDonStock.configurerConsultation(() ->
                Optional.of(consultation(StatutReservation.LIVREE, "REF-ANNULEE")));

        reconciliationJob.reconcilier();

        // Le statut doit rester ANNULEE : le job ne doit pas écraser une annulation
        Demande apres = demandeRepository.findById(demande.getId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutDemande.ANNULEE);
    }

    // -------------------------------------------------------------------------
    // Don & Stock injoignable pour ConsulterReservation
    // -------------------------------------------------------------------------

    @Test
    void donStockIndisponiblePourConsultation_demande_reste_en_lEtat_sans_erreur() {
        Demande demande = persisterDemande("H-REC-DOWN-CONSULT", StatutDemande.EN_ATTENTE);

        clientDonStock.configurerConsultation(() -> {
            throw new DonStockIndisponibleException("service indisponible");
        });

        // Ne doit pas lever d'exception : le job absorbe les erreurs et réessaie plus tard
        reconciliationJob.reconcilier();

        Demande apres = demandeRepository.findById(demande.getId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE);
    }

    // -------------------------------------------------------------------------
    // Aucune demande ouverte
    // -------------------------------------------------------------------------

    @Test
    void aucuneDemandeOuverte_leJobSexecuteSansErreur() {
        // Toutes les demandes existantes sont dans un état terminal.
        // Le job doit simplement s'arrêter sans lever d'exception.
        reconciliationJob.reconcilier();
        // Pas d'assertion : on vérifie uniquement l'absence d'exception
    }
}
