package com.service_demandes;

import com.service_demandes.client.DonStockClient;
import com.service_demandes.entity.Composant;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.MotifDemande;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.entity.Urgence;
import com.service_demandes.repository.DemandeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET, PATCH et le format d'erreur commun (400, 404, 405, 409). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemandeLectureModificationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    DemandeRepository demandes;
    @MockitoBean
    DonStockClient donStock;

    /** Chaque test utilise son propre hopitalId : la base H2 est partagee entre les tests. */
    private Demande demande(String hopitalId, StatutDemande statut, Instant date) {
        return demandes.save(Demande.builder()
                .hopitalId(hopitalId)
                .hopitalNom("Hôpital test")
                .patientRef("PAT1")
                .motif(MotifDemande.CHIRURGIE)
                .groupeSanguin(GroupeSanguin.B_POS)
                .composant(Composant.CGR)
                .quantitePoches(1)
                .urgence(Urgence.NORMALE)
                .statut(statut)
                .dateCreation(date)
                .build());
    }


    // ---------- GET ----------

    @Test
    void lireUneDemande_donne200() throws Exception {
        Demande d = demande("H-LIRE", StatutDemande.EN_ATTENTE, Instant.now());

        mvc.perform(get("/api/v1/demandes/{id}", d.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(d.getId().toString()))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.hopitalId").value("H-LIRE"));
    }

    @Test
    void demandeInconnue_donne404_avec_le_json_derreur_commun() throws Exception {
        UUID inconnu = UUID.randomUUID();

        mvc.perform(get("/api/v1/demandes/{id}", inconnu))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Demande introuvable"))
                .andExpect(jsonPath("$.path").value("/api/v1/demandes/" + inconnu))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void identifiantQuiNestPasUnUuid_donne400() throws Exception {
        mvc.perform(get("/api/v1/demandes/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("abc")));
    }

    @Test
    void liste_filtre_par_hopital_et_statut_de_la_plus_recente_a_la_plus_ancienne() throws Exception {
        Instant maintenant = Instant.now();
        demande("H-LISTE", StatutDemande.EN_ATTENTE, maintenant.minusSeconds(60));
        demande("H-LISTE", StatutDemande.SATISFAITE, maintenant);
        demande("H-AUTRE", StatutDemande.SATISFAITE, maintenant);

        mvc.perform(get("/api/v1/demandes").param("hopitalId", "H-LISTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].statut").value("SATISFAITE"))
                .andExpect(jsonPath("$[1].statut").value("EN_ATTENTE"));

        mvc.perform(get("/api/v1/demandes").param("hopitalId", "H-LISTE").param("statut", "EN_ATTENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].statut").value("EN_ATTENTE"));
    }

    @Test
    void filtreDeStatutInconnu_donne400_et_liste_les_valeurs_possibles() throws Exception {
        mvc.perform(get("/api/v1/demandes").param("statut", "BIZARRE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("EN_PENURIE")));
    }

    // ---------- PATCH ----------

    @Test
    void annulerUneDemandeOuverte_donne200() throws Exception {
        Demande d = demande("H-ANNULE", StatutDemande.EN_PENURIE, Instant.now());

        mvc.perform(patch("/api/v1/demandes/{id}", d.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"ANNULEE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULEE"));
    }

    @Test
    void annulerDeuxFois_donne409() throws Exception {
        Demande d = demande("H-2FOIS", StatutDemande.EN_ATTENTE, Instant.now());
        String corps = "{\"statut\":\"ANNULEE\"}";

        mvc.perform(patch("/api/v1/demandes/{id}", d.getId()).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/demandes/{id}", d.getId()).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void annulerUneDemandeSatisfaite_donne409() throws Exception {
        Demande d = demande("H-SATISFAITE", StatutDemande.SATISFAITE, Instant.now());

        mvc.perform(patch("/api/v1/demandes/{id}", d.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"ANNULEE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("SATISFAITE")));
    }

    @Test
    void corrigerLeCompteurDeCompensation_donne200() throws Exception {
        Demande d = demande("H-COMPTEUR", StatutDemande.EN_ATTENTE, Instant.now());

        mvc.perform(patch("/api/v1/demandes/{id}", d.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"compensationRecue\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compensationRecue").value(2))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
    }

    @Test
    void patchInvalide_donne400() throws Exception {
        Demande d = demande("H-PATCH400", StatutDemande.EN_ATTENTE, Instant.now());

        for (String corps : new String[]{"{}", "{\"statut\":\"SATISFAITE\"}", "{\"compensationRecue\":-1}", "{\"statut\":\"XYZ\"}"}) {
            mvc.perform(patch("/api/v1/demandes/{id}", d.getId()).contentType(MediaType.APPLICATION_JSON).content(corps))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void patchSurUneDemandeInconnue_donne404() throws Exception {
        mvc.perform(patch("/api/v1/demandes/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"ANNULEE\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- erreurs HTTP standard au meme format ----------

    @Test
    void methodeNonSupportee_donne405_au_format_commun() throws Exception {
        mvc.perform(delete("/api/v1/demandes"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.path").value("/api/v1/demandes"));
    }

    @Test
    void routeInconnue_donne404_au_format_commun() throws Exception {
        mvc.perform(get("/api/v1/nimporte-quoi"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
