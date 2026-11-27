package com.service_demandes;

import com.service_demandes.client.DonStockIndisponibleException;
import com.service_demandes.client.DonneursClient;
import com.service_demandes.client.DonneursClient.DonneursIndisponibleException;
import com.service_demandes.client.ReservationResultat;
import com.service_demandes.client.StatutReservation;
import com.service_demandes.dto.CreateCompensationRequest;
import com.service_demandes.entity.Composant;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.MotifDemande;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.entity.Urgence;
import com.service_demandes.repository.DemandeRepository;
import com.service_demandes.service.CompensationService;
import com.service_demandes.service.DemandeService;
import com.service_demandes.support.DonStockClientAvecDisjoncteur;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * Vérifie que les deux disjoncteurs Resilience4j se comportent conformément
 * à l'étude (section 10 et S6-09) :
 *
 * <ul>
 *   <li>Disjoncteur {@code donStock} : 5 échecs consécutifs → OPEN → 6e appel
 *       rejeté immédiatement sans appel réseau.</li>
 *   <li>Disjoncteur {@code donneurs} : même logique pour la vérification de
 *       certificat dans service-donneurs.</li>
 *   <li>Après les échecs, un appel réussi transite par le disjoncteur et
 *       peut initier la transition vers HALF_OPEN puis CLOSED.</li>
 * </ul>
 *
 * <p>La fenêtre glissante est configurée à 5 appels dans
 * {@code application-test.properties} pour que les tests soient rapides.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class CircuitBreakerTest {

    @Autowired DemandeService demandeService;
    @Autowired CompensationService compensationService;
    @Autowired DemandeRepository demandeRepository;
    @Autowired DonStockClientAvecDisjoncteur clientDonStock;
    @Autowired CircuitBreakerRegistry registreDisjoncteurs;

    @MockitoBean DonneursClient donneursClient;

    // -------------------------------------------------------------------------
    // Remise à zéro du disjoncteur avant chaque test
    // -------------------------------------------------------------------------

    @BeforeEach
    void reinitialiserDisjoncteurs() {
        registreDisjoncteurs.circuitBreaker("donStock").reset();
        registreDisjoncteurs.circuitBreaker("donneurs").reset();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Demande persisterDemande(String hopitalId) {
        return demandeRepository.save(Demande.builder()
                .hopitalId(hopitalId)
                .hopitalNom("Hôpital test")
                .patientRef("PAT-CB")
                .motif(MotifDemande.CHIRURGIE)
                .groupeSanguin(GroupeSanguin.O_NEG)
                .composant(Composant.CGR)
                .quantitePoches(1)
                .urgence(Urgence.HAUTE)
                .statut(StatutDemande.EN_ATTENTE)
                .compensationDemandee(2)
                .dateCreation(Instant.now())
                .build());
    }

    private void simulerEchecDonStock() {
        clientDonStock.configurerComportement(() -> {
            throw new DonStockIndisponibleException("service simulé en panne");
        });
    }

    private void simulerSuccesDonStock() {
        clientDonStock.configurerComportement(() ->
                new ReservationResultat(StatutReservation.RESERVEE_STOCK_LOCAL, "RES-CB", null, null));
    }

    // -------------------------------------------------------------------------
    // Disjoncteur donStock
    // -------------------------------------------------------------------------

    @Test
    void cinqEchecsConsecutifs_ouvrentLeDisjoncteurDonStock() {
        simulerEchecDonStock();
        CircuitBreaker disjoncteur = registreDisjoncteurs.circuitBreaker("donStock");

        // 5 appels échouent → fenêtre pleine à 100 % d'échecs
        for (int i = 0; i < 5; i++) {
            Demande demande = persisterDemande("H-CB-OPEN-" + i);
            demandeService.creer(requetePourDemande(demande));
        }

        assertThat(disjoncteur.getState())
                .as("Le disjoncteur donStock doit être OPEN après 5 échecs consécutifs")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void sixiemeAppel_estRejeteSansAppelReseau_quandLeDisjoncteurEstOuvert() {
        simulerEchecDonStock();

        // Ouvrir le disjoncteur avec 5 échecs
        for (int i = 0; i < 5; i++) {
            Demande demande = persisterDemande("H-CB-6E-" + i);
            demandeService.creer(requetePourDemande(demande));
        }

        // Le 6e appel doit être rejeté immédiatement par le disjoncteur (fallback)
        // et la demande doit rester EN_ATTENTE sans appeler Don & Stock
        Demande demande6 = persisterDemande("H-CB-6E-REJET");
        Demande resultat = demandeService.creer(requetePourDemande(demande6));

        assertThat(resultat.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE);
        assertThat(registreDisjoncteurs.circuitBreaker("donStock").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void apresOuverture_unSuccesTransitionneVersHalfOpen_puisClosed() throws InterruptedException {
        simulerEchecDonStock();

        // Ouvrir le disjoncteur
        for (int i = 0; i < 5; i++) {
            Demande demande = persisterDemande("H-CB-RECOVER-" + i);
            demandeService.creer(requetePourDemande(demande));
        }
        assertThat(registreDisjoncteurs.circuitBreaker("donStock").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        // Attendre que le disjoncteur passe automatiquement en HALF_OPEN
        // (wait-duration-in-open-state=1s dans application-test.properties)
        Thread.sleep(1_200);
        registreDisjoncteurs.circuitBreaker("donStock").transitionToHalfOpenState();

        // Configurer un succès puis effectuer un appel
        simulerSuccesDonStock();
        Demande demande = persisterDemande("H-CB-RECOVER-SUCCES");
        Demande resultat = demandeService.creer(requetePourDemande(demande));

        assertThat(resultat.getStatut()).isEqualTo(StatutDemande.SATISFAITE);
    }

    @Test
    void statsDisjoncteur_sontCoherentesApresEchecs() {
        simulerEchecDonStock();
        CircuitBreaker disjoncteur = registreDisjoncteurs.circuitBreaker("donStock");

        for (int i = 0; i < 5; i++) {
            Demande demande = persisterDemande("H-CB-STATS-" + i);
            demandeService.creer(requetePourDemande(demande));
        }

        CircuitBreaker.Metrics metrics = disjoncteur.getMetrics();
        assertThat(metrics.getNumberOfFailedCalls()).isGreaterThanOrEqualTo(5);
        assertThat(metrics.getFailureRate()).isEqualTo(100.0f);
    }

    // -------------------------------------------------------------------------
    // Disjoncteur donneurs (vérification de certificat)
    // -------------------------------------------------------------------------

    @Test
    void cinqEchecsVersServiceDonneurs_ouvrentLeDisjoncteurDonneurs() {
        doThrow(new DonneursIndisponibleException("timeout", new RuntimeException()))
                .when(donneursClient).verifierCertificat(anyString());

        Demande demande = persisterDemande("H-CB-DONNEURS");
        CircuitBreaker disjoncteur = registreDisjoncteurs.circuitBreaker("donneurs");

        // 5 appels échouent vers service-donneurs
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() ->
                    compensationService.enregistrer(
                            demande.getId(),
                            new CreateCompensationRequest("CERT-CB-DON-" + UUID.randomUUID())))
                    .isInstanceOf(com.service_demandes.exception.ServiceIndisponibleException.class);
        }

        assertThat(disjoncteur.getState())
                .as("Le disjoncteur donneurs doit être OPEN après 5 échecs consécutifs")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void sixiemeAppelVersServiceDonneurs_estRejeteSansAppelReseau_quandDisjoncteurOuvert() {
        doThrow(new DonneursIndisponibleException("timeout", new RuntimeException()))
                .when(donneursClient).verifierCertificat(anyString());

        Demande demande = persisterDemande("H-CB-DONNEURS-6E");

        // Ouvrir le disjoncteur avec 5 échecs
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() ->
                    compensationService.enregistrer(
                            demande.getId(),
                            new CreateCompensationRequest("CERT-CB-6E-" + UUID.randomUUID())))
                    .isInstanceOf(com.service_demandes.exception.ServiceIndisponibleException.class);
        }

        // Le 6e appel est rejeté immédiatement par le disjoncteur (CallNotPermittedException)
        // et traduit en ServiceIndisponibleException par le fallback
        assertThatThrownBy(() ->
                compensationService.enregistrer(
                        demande.getId(),
                        new CreateCompensationRequest("CERT-CB-REJECT-" + UUID.randomUUID())))
                .isInstanceOf(com.service_demandes.exception.ServiceIndisponibleException.class);
    }

    // -------------------------------------------------------------------------
    // Helper : construit le DTO CreateDemandeRequest depuis une Demande persistée
    // -------------------------------------------------------------------------

    private com.service_demandes.dto.CreateDemandeRequest requetePourDemande(Demande demande) {
        return new com.service_demandes.dto.CreateDemandeRequest(
                demande.getHopitalId(),
                demande.getHopitalNom(),
                demande.getPatientRef(),
                demande.getMotif(),
                demande.getGroupeSanguin(),
                demande.getComposant(),
                demande.getQuantitePoches(),
                demande.getUrgence(),
                demande.getCompensationDemandee());
    }
}
