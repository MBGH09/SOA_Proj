package com.service_demandes;

import com.service_demandes.client.DonStockClient;
import com.service_demandes.dto.CreateAppelDonRequest;
import com.service_demandes.entity.AppelDon;
import com.service_demandes.entity.Composant;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.MotifDemande;
import com.service_demandes.entity.StatutAppelDon;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.entity.Urgence;
import com.service_demandes.exception.RessourceIntrouvableException;
import com.service_demandes.repository.AppelDonRepository;
import com.service_demandes.repository.DemandeRepository;
import com.service_demandes.service.AppelDonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires et d'intégration de {@link AppelDonService}.
 *
 * <p>Couverture :</p>
 * <ul>
 *   <li>Ouverture automatique sur pénurie (R16)</li>
 *   <li>Réutilisation d'un appel déjà OUVERT au lieu d'en créer un doublon (R16)</li>
 *   <li>Ouverture manuelle avec et sans demande associée</li>
 *   <li>Ouverture manuelle avec une demandeId inconnue → 404</li>
 *   <li>Fermeture manuelle et idempotence</li>
 *   <li>Fermeture automatique par groupe + composant</li>
 *   <li>Liste publique avec et sans filtre de statut</li>
 *   <li>Lecture par id → 404 si inexistant</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class AppelDonServiceTest {

    @Autowired
    AppelDonService appelDonService;
    @Autowired
    AppelDonRepository appelDonRepository;
    @Autowired
    DemandeRepository demandeRepository;

    // Don & Stock n'est pas utilisé ici mais il est dans le contexte Spring
    @MockitoBean
    DonStockClient donStockClient;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Demande persisterDemandeEnPenurie(String hopitalId) {
        return demandeRepository.save(Demande.builder()
                .hopitalId(hopitalId)
                .hopitalNom("Hôpital test")
                .patientRef("PAT-TEST")
                .motif(MotifDemande.CHIRURGIE)
                .groupeSanguin(GroupeSanguin.O_NEG)
                .composant(Composant.CGR)
                .quantitePoches(2)
                .urgence(Urgence.HAUTE)
                .statut(StatutDemande.EN_PENURIE)
                .dateCreation(Instant.now())
                .build());
    }

    // -------------------------------------------------------------------------
    // Ouverture automatique sur pénurie (R16)
    // -------------------------------------------------------------------------

    @Test
    void ouvrirOuReutiliser_creeUnNouvelAppelSiAucunNestOuvert() {
        Demande demandeEnPenurie = persisterDemandeEnPenurie("H-AUTO-CREER");

        AppelDon appelCree = appelDonService.ouvrirOuReutiliser(demandeEnPenurie);

        assertThat(appelCree.getId()).isNotNull();
        assertThat(appelCree.getStatut()).isEqualTo(StatutAppelDon.OUVERT);
        assertThat(appelCree.getGroupeSanguin()).isEqualTo(GroupeSanguin.O_NEG);
        assertThat(appelCree.getComposant()).isEqualTo(Composant.CGR);
        assertThat(appelCree.getDemande().getId()).isEqualTo(demandeEnPenurie.getId());
        assertThat(appelCree.getDateOuverture()).isNotNull();
    }

    @Test
    void ouvrirOuReutiliser_retourneLAppelExistantSiUnEstDejaOuvertPourCeGroupeEtComposant() {
        Demande premierePenurie = persisterDemandeEnPenurie("H-REUTILISER-1");
        Demande deuxiemePenurie  = persisterDemandeEnPenurie("H-REUTILISER-2");

        AppelDon premierAppel = appelDonService.ouvrirOuReutiliser(premierePenurie);
        AppelDon deuxiemeAppel = appelDonService.ouvrirOuReutiliser(deuxiemePenurie);

        // R16 : un seul appel ouvert par groupe + composant, pas de doublon
        assertThat(deuxiemeAppel.getId()).isEqualTo(premierAppel.getId());
        assertThat(appelDonRepository.findByStatut(StatutAppelDon.OUVERT).stream()
                .filter(a -> a.getGroupeSanguin() == GroupeSanguin.O_NEG
                          && a.getComposant() == Composant.CGR)
                .count()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Ouverture manuelle (POST /api/v1/appels)
    // -------------------------------------------------------------------------

    @Test
    void ouvrir_creeUnAppelSansDemande_quandAucunIdNestFourni() {
        CreateAppelDonRequest corps = new CreateAppelDonRequest(
                GroupeSanguin.AB_NEG, Composant.PLASMA, null, null);

        AppelDon appel = appelDonService.ouvrir(corps);

        assertThat(appel.getId()).isNotNull();
        assertThat(appel.getStatut()).isEqualTo(StatutAppelDon.OUVERT);
        assertThat(appel.getDemande()).isNull();
        assertThat(appel.getGroupeSanguin()).isEqualTo(GroupeSanguin.AB_NEG);
        assertThat(appel.getComposant()).isEqualTo(Composant.PLASMA);
    }

    @Test
    void ouvrir_associeLeDemandeSiUnIdValideEstFourni() {
        Demande demande = persisterDemandeEnPenurie("H-OUVRIR-AVEC-DEMANDE");
        CreateAppelDonRequest corps = new CreateAppelDonRequest(
                GroupeSanguin.B_NEG, Composant.CGR, demande.getId(), "CRTS-SFX");

        AppelDon appel = appelDonService.ouvrir(corps);

        assertThat(appel.getDemande()).isNotNull();
        assertThat(appel.getDemande().getId()).isEqualTo(demande.getId());
        assertThat(appel.getCentreCibleId()).isEqualTo("CRTS-SFX");
    }

    @Test
    void ouvrir_leve404_quandLaDemandeAssocieeEstInconnue() {
        UUID idInexistant = UUID.randomUUID();
        CreateAppelDonRequest corps = new CreateAppelDonRequest(
                GroupeSanguin.A_NEG, Composant.CGR, idInexistant, null);

        assertThatThrownBy(() -> appelDonService.ouvrir(corps))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining(idInexistant.toString());
    }

    // -------------------------------------------------------------------------
    // Fermeture manuelle (PATCH /api/v1/appels/{id})
    // -------------------------------------------------------------------------

    @Test
    void fermer_passeLAppelEnFERME_et_enregistreLaDateDeFermeture() {
        Demande demande = persisterDemandeEnPenurie("H-FERMER");
        AppelDon appelOuvert = appelDonService.ouvrirOuReutiliser(demande);

        AppelDon appelFerme = appelDonService.fermer(appelOuvert.getId());

        assertThat(appelFerme.getStatut()).isEqualTo(StatutAppelDon.FERME);
        assertThat(appelFerme.getDateFermeture()).isNotNull();
    }

    @Test
    void fermer_estIdempotent_unAppelDejaFermeRetourne200SansErreur() {
        Demande demande = persisterDemandeEnPenurie("H-FERMER-IDEMPOTENT");
        AppelDon appelOuvert = appelDonService.ouvrirOuReutiliser(demande);
        appelDonService.fermer(appelOuvert.getId());

        // Deuxième fermeture : ne doit pas lever d'exception
        AppelDon resultat = appelDonService.fermer(appelOuvert.getId());

        assertThat(resultat.getStatut()).isEqualTo(StatutAppelDon.FERME);
    }

    @Test
    void fermer_leve404_quandLAppelEstInconnu() {
        assertThatThrownBy(() -> appelDonService.fermer(UUID.randomUUID()))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    // -------------------------------------------------------------------------
    // Fermeture automatique par groupe + composant (ReconciliationJob)
    // -------------------------------------------------------------------------

    @Test
    void fermerTousLesAppelsOuvertsPour_fermeUniquementLesCombinaisonsCorrespondantes() {
        // Crée deux appels O_NEG/CGR et un B_POS/PLASMA
        Demande d1 = persisterDemandeEnPenurie("H-AUTO-FERMER-1");
        appelDonRepository.save(AppelDon.builder()
                .demande(d1)
                .groupeSanguin(GroupeSanguin.O_NEG)
                .composant(Composant.CGR)
                .statut(StatutAppelDon.OUVERT)
                .build());
        appelDonRepository.save(AppelDon.builder()
                .groupeSanguin(GroupeSanguin.B_POS)
                .composant(Composant.PLASMA)
                .statut(StatutAppelDon.OUVERT)
                .build());

        int nombreFermes = appelDonService.fermerTousLesAppelsOuvertsPour(
                GroupeSanguin.O_NEG, Composant.CGR);

        assertThat(nombreFermes).isGreaterThanOrEqualTo(1);
        // L'appel B_POS/PLASMA doit rester ouvert
        List<AppelDon> encoreOuverts = appelDonRepository.findByStatut(StatutAppelDon.OUVERT);
        assertThat(encoreOuverts).anyMatch(
                a -> a.getGroupeSanguin() == GroupeSanguin.B_POS && a.getComposant() == Composant.PLASMA);
    }

    @Test
    void fermerTousLesAppelsOuvertsPour_retourneZero_quandAucunNestOuvert() {
        int nombreFermes = appelDonService.fermerTousLesAppelsOuvertsPour(
                GroupeSanguin.AB_POS, Composant.PLAQUETTES);

        assertThat(nombreFermes).isZero();
    }

    // -------------------------------------------------------------------------
    // Lecture publique (GET /api/v1/appels)
    // -------------------------------------------------------------------------

    @Test
    void lister_sansFiltre_retourneTousLesAppels() {
        appelDonRepository.save(AppelDon.builder()
                .groupeSanguin(GroupeSanguin.A_POS)
                .composant(Composant.CGR)
                .statut(StatutAppelDon.OUVERT)
                .build());
        appelDonRepository.save(AppelDon.builder()
                .groupeSanguin(GroupeSanguin.A_POS)
                .composant(Composant.CGR)
                .statut(StatutAppelDon.FERME)
                .build());

        List<AppelDon> tous = appelDonService.lister(null);

        assertThat(tous).isNotEmpty();
    }

    @Test
    void lister_avecFiltreOUVERT_retourneSeulementLesAppelsOuverts() {
        appelDonRepository.save(AppelDon.builder()
                .groupeSanguin(GroupeSanguin.O_POS)
                .composant(Composant.CGR)
                .statut(StatutAppelDon.FERME)
                .build());

        List<AppelDon> ouverts = appelDonService.lister(StatutAppelDon.OUVERT);

        assertThat(ouverts).allMatch(a -> a.getStatut() == StatutAppelDon.OUVERT);
    }

    @Test
    void lire_retourneLAppel_quandIlExiste() {
        AppelDon appel = appelDonRepository.save(AppelDon.builder()
                .groupeSanguin(GroupeSanguin.B_NEG)
                .composant(Composant.PLASMA)
                .statut(StatutAppelDon.OUVERT)
                .build());

        AppelDon relu = appelDonService.lire(appel.getId());

        assertThat(relu.getId()).isEqualTo(appel.getId());
    }

    @Test
    void lire_leve404_quandLAppelEstInconnu() {
        assertThatThrownBy(() -> appelDonService.lire(UUID.randomUUID()))
                .isInstanceOf(RessourceIntrouvableException.class);
    }
}
