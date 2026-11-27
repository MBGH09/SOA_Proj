package com.service_demandes;

import com.service_demandes.client.DonStockClient;
import com.service_demandes.client.DonneursClient;
import com.service_demandes.client.DonneursClient.CertificatInvalideException;
import com.service_demandes.client.DonneursClient.DonneursIndisponibleException;
import com.service_demandes.dto.CreateCompensationRequest;
import com.service_demandes.entity.Compensation;
import com.service_demandes.entity.Composant;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.MotifDemande;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.entity.Urgence;
import com.service_demandes.exception.ConflitMetierException;
import com.service_demandes.exception.RequeteInvalideException;
import com.service_demandes.exception.RessourceIntrouvableException;
import com.service_demandes.exception.ServiceIndisponibleException;
import com.service_demandes.repository.CompensationRepository;
import com.service_demandes.repository.DemandeRepository;
import com.service_demandes.service.CompensationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de {@link CompensationService} et du controller associé.
 *
 * <p>Couverture :</p>
 * <ul>
 *   <li>R17 : demande sans {@code compensationDemandee} → 400</li>
 *   <li>R26 : même numéro de certificat deux fois → 409</li>
 *   <li>Certificat invalide selon Donneurs → 400</li>
 *   <li>service-donneurs injoignable → 503</li>
 *   <li>Demande inconnue → 404</li>
 *   <li>Enregistrement valide : compteur incrémenté, compensation persistée</li>
 *   <li>Lecture de la liste des compensations d'une demande</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompensationServiceTest {

    @Autowired MockMvc mvc;
    @Autowired CompensationService compensationService;
    @Autowired DemandeRepository demandeRepository;
    @Autowired CompensationRepository compensationRepository;

    @MockitoBean DonStockClient donStockClient;
    @MockitoBean DonneursClient donneursClient;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Demande persisterDemandeAvecCompensation(String hopitalId, Integer compensationDemandee) {
        return demandeRepository.save(Demande.builder()
                .hopitalId(hopitalId)
                .hopitalNom("Hôpital test")
                .patientRef("PAT-COMP")
                .motif(MotifDemande.CHIRURGIE)
                .groupeSanguin(GroupeSanguin.O_NEG)
                .composant(Composant.CGR)
                .quantitePoches(1)
                .urgence(Urgence.NORMALE)
                .statut(StatutDemande.APPROVISIONNEMENT_EN_COURS)
                .compensationDemandee(compensationDemandee)
                .dateCreation(Instant.now())
                .build());
    }

    private Demande persisterDemandeSansCompensation(String hopitalId) {
        return persisterDemandeAvecCompensation(hopitalId, null);
    }

    // -------------------------------------------------------------------------
    // R17 — La demande doit avoir compensationDemandee ≥ 1
    // -------------------------------------------------------------------------

    @Test
    void enregistrer_leve400_quandLaDemandeNaPasDeCompensationDemandee() {
        Demande demandesSansCompensation = persisterDemandeSansCompensation("H-COMP-R17-NULL");

        assertThatThrownBy(() ->
                compensationService.enregistrer(
                        demandesSansCompensation.getId(),
                        new CreateCompensationRequest("CERT-2026-000001")))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("compensation");

        verify(donneursClient, never()).verifierCertificat(anyString());
    }

    @Test
    void enregistrer_via_controleur_leve400_quandDemandeNaDemandeAucuneCompensation() throws Exception {
        Demande demande = persisterDemandeSansCompensation("H-COMP-R17-HTTP");

        mvc.perform(post("/api/v1/demandes/{id}/compensations", demande.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCertificat\": \"CERT-2026-R17\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // -------------------------------------------------------------------------
    // R26 — Un numéro de certificat ne compte qu'une seule fois
    // -------------------------------------------------------------------------

    @Test
    void enregistrer_leve409_quandLeMemeNumeroDeCertificatEstDejaUtilise() {
        Demande demande = persisterDemandeAvecCompensation("H-COMP-R26", 2);
        when(donneursClient.verifierCertificat(anyString())).thenReturn(true);
        String numeroCertificat = "CERT-R26-" + UUID.randomUUID();

        // Premier enregistrement : doit réussir
        compensationService.enregistrer(demande.getId(), new CreateCompensationRequest(numeroCertificat));

        // Deuxième enregistrement avec le même numéro : doit échouer (R26)
        assertThatThrownBy(() ->
                compensationService.enregistrer(
                        demande.getId(),
                        new CreateCompensationRequest(numeroCertificat)))
                .isInstanceOf(ConflitMetierException.class)
                .hasMessageContaining(numeroCertificat);
    }

    @Test
    void enregistrer_via_controleur_leve409_quandNumeroCertificatDejauUtilise() throws Exception {
        Demande demande = persisterDemandeAvecCompensation("H-COMP-R26-HTTP", 3);
        when(donneursClient.verifierCertificat(anyString())).thenReturn(true);
        String numeroCertificat = "CERT-HTTP-R26-" + UUID.randomUUID();

        // Premier appel réussit
        mvc.perform(post("/api/v1/demandes/{id}/compensations", demande.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCertificat\": \"" + numeroCertificat + "\"}"))
                .andExpect(status().isCreated());

        // Deuxième appel avec le même numéro → 409
        mvc.perform(post("/api/v1/demandes/{id}/compensations", demande.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCertificat\": \"" + numeroCertificat + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(numeroCertificat)));
    }

    // -------------------------------------------------------------------------
    // Certificat invalide selon Donneurs → 400
    // -------------------------------------------------------------------------

    @Test
    void enregistrer_leve400_quandDonneursSignaleQueLeCertificatEstInvalide() {
        Demande demande = persisterDemandeAvecCompensation("H-COMP-CERT-INVALIDE", 2);
        doThrow(new CertificatInvalideException("Certificat inconnu"))
                .when(donneursClient).verifierCertificat(anyString());

        assertThatThrownBy(() ->
                compensationService.enregistrer(
                        demande.getId(),
                        new CreateCompensationRequest("CERT-INEXISTANT")))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("CERT-INEXISTANT");
    }

    // -------------------------------------------------------------------------
    // service-donneurs injoignable → 503
    // -------------------------------------------------------------------------

    @Test
    void enregistrer_leve503_quandServiceDonneursEstInjoignable() {
        Demande demande = persisterDemandeAvecCompensation("H-COMP-503", 2);
        doThrow(new DonneursIndisponibleException("Timeout", new RuntimeException()))
                .when(donneursClient).verifierCertificat(anyString());

        assertThatThrownBy(() ->
                compensationService.enregistrer(
                        demande.getId(),
                        new CreateCompensationRequest("CERT-DOWN")))
                .isInstanceOf(ServiceIndisponibleException.class)
                .hasMessageContaining("PATCH");
        // Le message doit indiquer au staff comment corriger manuellement
    }

    @Test
    void enregistrer_via_controleur_leve503_quandDonneursEstInjoignable() throws Exception {
        Demande demande = persisterDemandeAvecCompensation("H-COMP-503-HTTP", 2);
        doThrow(new DonneursIndisponibleException("Timeout", new RuntimeException()))
                .when(donneursClient).verifierCertificat(anyString());

        mvc.perform(post("/api/v1/demandes/{id}/compensations", demande.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCertificat\": \"CERT-DOWN\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("PATCH")));
    }

    // -------------------------------------------------------------------------
    // Demande inconnue → 404
    // -------------------------------------------------------------------------

    @Test
    void enregistrer_leve404_quandLaDemandeEstInconnue() {
        UUID idInexistant = UUID.randomUUID();

        assertThatThrownBy(() ->
                compensationService.enregistrer(
                        idInexistant,
                        new CreateCompensationRequest("CERT-404")))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining(idInexistant.toString());

        verify(donneursClient, never()).verifierCertificat(anyString());
    }

    // -------------------------------------------------------------------------
    // Enregistrement valide : compteur et persistance
    // -------------------------------------------------------------------------

    @Test
    void enregistrer_incrementeLeCompteurEtPersisteLaCompensation() {
        Demande demande = persisterDemandeAvecCompensation("H-COMP-OK", 2);
        when(donneursClient.verifierCertificat(anyString())).thenReturn(true);
        String numeroCertificat = "CERT-OK-" + UUID.randomUUID();

        Compensation compensation = compensationService.enregistrer(
                demande.getId(), new CreateCompensationRequest(numeroCertificat));

        assertThat(compensation.getId()).isNotNull();
        assertThat(compensation.getNumeroCertificat()).isEqualTo(numeroCertificat);
        assertThat(compensation.getDateEnregistrement()).isNotNull();

        Demande demandeApres = demandeRepository.findById(demande.getId()).orElseThrow();
        assertThat(demandeApres.getCompensationRecue()).isEqualTo(1);
    }

    @Test
    void enregistrer_deuxNumerosDifferents_compteurAtteintDeux() {
        Demande demande = persisterDemandeAvecCompensation("H-COMP-2OK", 2);
        when(donneursClient.verifierCertificat(anyString())).thenReturn(true);
        String cert1 = "CERT-OK1-" + UUID.randomUUID();
        String cert2 = "CERT-OK2-" + UUID.randomUUID();

        compensationService.enregistrer(demande.getId(), new CreateCompensationRequest(cert1));
        compensationService.enregistrer(demande.getId(), new CreateCompensationRequest(cert2));

        Demande demandeApres = demandeRepository.findById(demande.getId()).orElseThrow();
        assertThat(demandeApres.getCompensationRecue()).isEqualTo(2);
    }

    @Test
    void enregistrer_via_controleur_donne201_et_compensationRecueEgale1() throws Exception {
        Demande demande = persisterDemandeAvecCompensation("H-COMP-201", 2);
        when(donneursClient.verifierCertificat(anyString())).thenReturn(true);
        String numeroCertificat = "CERT-201-" + UUID.randomUUID();

        mvc.perform(post("/api/v1/demandes/{id}/compensations", demande.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCertificat\": \"" + numeroCertificat + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroCertificat").value(numeroCertificat))
                .andExpect(jsonPath("$.compensationRecue").value(1))
                .andExpect(jsonPath("$.demandeId").value(demande.getId().toString()));
    }

    // -------------------------------------------------------------------------
    // Lecture de la liste des compensations
    // -------------------------------------------------------------------------

    @Test
    void lister_retourneToutesLesCompensationsEnregistreesParDemande() {
        Demande demande = persisterDemandeAvecCompensation("H-COMP-LIST", 3);
        when(donneursClient.verifierCertificat(anyString())).thenReturn(true);
        String cert1 = "CERT-LIST1-" + UUID.randomUUID();
        String cert2 = "CERT-LIST2-" + UUID.randomUUID();

        compensationService.enregistrer(demande.getId(), new CreateCompensationRequest(cert1));
        compensationService.enregistrer(demande.getId(), new CreateCompensationRequest(cert2));

        List<Compensation> liste = compensationService.lister(demande.getId());
        assertThat(liste).hasSize(2);
        assertThat(liste).extracting(Compensation::getNumeroCertificat)
                .containsExactlyInAnyOrder(cert1, cert2);
    }

    @Test
    void lister_leve404_quandLaDemandeEstInconnue() {
        assertThatThrownBy(() -> compensationService.lister(UUID.randomUUID()))
                .isInstanceOf(RessourceIntrouvableException.class);
    }
}
