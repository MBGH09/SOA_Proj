package com.service_demandes;

import com.service_demandes.client.DonStockClient;
import com.service_demandes.client.DonStockIndisponibleException;
import com.service_demandes.client.ReservationCommande;
import com.service_demandes.client.ReservationResultat;
import com.service_demandes.client.StatutReservation;
import com.service_demandes.entity.Composant;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.entity.Urgence;
import com.service_demandes.repository.DemandeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les cinq lignes du tableau de resultats (etude 7.3) : 201, 202 x3 et 400.
 * Don & Stock est simule avec Mockito ; la base est H2 avec la vraie migration Flyway.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemandeControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    DemandeRepository demandes;
    @MockitoBean
    DonStockClient donStock;

    private static String corps(String groupe, int quantite, String patientRef, String extra) {
        return """
                {
                  "hopitalId": "HOP-SFX-01",
                  "hopitalNom": "CHU Habib Bourguiba Sfax",
                  "patientRef": "%s",
                  "motif": "CHIRURGIE",
                  "groupeSanguin": "%s",
                  "composant": "CGR",
                  "quantitePoches": %d,
                  "urgence": "HAUTE"%s
                }
                """.formatted(patientRef, groupe, quantite, extra);
    }

    private static String corpsValide() {
        return corps("O_NEG", 3, "PAT20260042", ", \"compensationDemandee\": 2");
    }

    @Test
    void stockLocalSuffisant_donne201_et_SATISFAITE() throws Exception {
        when(donStock.verifierEtReserver(any()))
                .thenReturn(new ReservationResultat(StatutReservation.RESERVEE_STOCK_LOCAL, "RES-1", null, null));

        mvc.perform(post("/api/v1/demandes").contentType(MediaType.APPLICATION_JSON).content(corpsValide()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("SATISFAITE"))
                .andExpect(jsonPath("$.referenceApprovisionnement").value("RES-1"))
                .andExpect(jsonPath("$.compensationDemandee").value(2))
                .andExpect(jsonPath("$.compensationRecue").value(0))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void commandeCentrale_donne202_et_APPROVISIONNEMENT_EN_COURS() throws Exception {
        when(donStock.verifierEtReserver(any()))
                .thenReturn(new ReservationResultat(StatutReservation.APPROVISIONNEMENT_COMMANDE, "APP-77", "CRTS Sfax", 120));

        mvc.perform(post("/api/v1/demandes").contentType(MediaType.APPLICATION_JSON).content(corpsValide()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statut").value("APPROVISIONNEMENT_EN_COURS"))
                .andExpect(jsonPath("$.referenceApprovisionnement").value("APP-77"));
    }

    @Test
    void personneNaLeSang_donne202_et_EN_PENURIE() throws Exception {
        when(donStock.verifierEtReserver(any()))
                .thenReturn(new ReservationResultat(StatutReservation.STOCK_INSUFFISANT_PARTOUT, null, null, null));

        mvc.perform(post("/api/v1/demandes").contentType(MediaType.APPLICATION_JSON).content(corpsValide()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statut").value("EN_PENURIE"));
    }

    @Test
    void stockIndisponible_donne202_et_la_demande_reste_EN_ATTENTE() throws Exception {
        when(donStock.verifierEtReserver(any())).thenThrow(new DonStockIndisponibleException("deadline"));
        long avant = demandes.count();

        mvc.perform(post("/api/v1/demandes").contentType(MediaType.APPLICATION_JSON).content(corpsValide()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));

        assertThat(demandes.count()).isEqualTo(avant + 1);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            quantité zéro (R11)        | O_NEG   | 0 | PAT1  | 
            quantité négative          | O_NEG   | -2| PAT1  | 
            groupe inconnu             | X_POS   | 1 | PAT1  | 
            référence patient invalide | O_NEG   | 1 | Jean Dupont! |
            compensation de 4 (R17)    | O_NEG   | 1 | PAT1  | , "compensationDemandee": 4
            compensation de 1 (R17)    | O_NEG   | 1 | PAT1  | , "compensationDemandee": 1
            """)
    void donneesInvalides_donnent400_rien_nest_enregistre_et_Don_Stock_nest_pas_appele(
            String cas, String groupe, int quantite, String patientRef, String extra) throws Exception {
        long avant = demandes.count();

        mvc.perform(post("/api/v1/demandes").contentType(MediaType.APPLICATION_JSON)
                        .content(corps(groupe.trim(), quantite, patientRef.trim(), extra == null ? "" : extra)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/demandes"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        assertThat(demandes.count()).isEqualTo(avant);
        verify(donStock, never()).verifierEtReserver(any());
    }

    @Test
    void champsObligatoiresManquants_donnent400() throws Exception {
        mvc.perform(post("/api/v1/demandes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("quantitePoches")));
    }

    @Test
    void jsonIllisible_donne400() throws Exception {
        mvc.perform(post("/api/v1/demandes").contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void donStockRecoitLidDeLaDemande_lhopital_et_la_quantite() throws Exception {
        when(donStock.verifierEtReserver(any()))
                .thenReturn(new ReservationResultat(StatutReservation.RESERVEE_STOCK_LOCAL, "RES-2", null, null));

        mvc.perform(post("/api/v1/demandes").contentType(MediaType.APPLICATION_JSON).content(corpsValide()))
                .andExpect(status().isCreated());

        ArgumentCaptor<ReservationCommande> capture = ArgumentCaptor.forClass(ReservationCommande.class);
        verify(donStock).verifierEtReserver(capture.capture());
        ReservationCommande commande = capture.getValue();
        Demande enBase = demandes.findById(commande.demandeId()).orElseThrow();

        assertThat(commande.lieuId()).isEqualTo("HOP-SFX-01");
        assertThat(commande.groupeSanguin()).isEqualTo(GroupeSanguin.O_NEG);
        assertThat(commande.composant()).isEqualTo(Composant.CGR);
        assertThat(commande.quantite()).isEqualTo(3);
        assertThat(commande.urgence()).isEqualTo(Urgence.HAUTE);
        assertThat(enBase.getStatut()).isEqualTo(StatutDemande.SATISFAITE);
    }
}
