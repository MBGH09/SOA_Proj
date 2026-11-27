package com.service_demandes;

import com.service_demandes.entity.AppelDon;
import com.service_demandes.entity.Composant;
import com.service_demandes.entity.Compensation;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.MotifDemande;
import com.service_demandes.entity.StatutAppelDon;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.entity.Urgence;
import com.service_demandes.repository.AppelDonRepository;
import com.service_demandes.repository.CompensationRepository;
import com.service_demandes.repository.DemandeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifie que la migration V1 demarre sur une base vide et que les entites correspondent aux tables. */
@SpringBootTest
@ActiveProfiles("test")
class SchemaTest {

    @Autowired
    DemandeRepository demandes;
    @Autowired
    AppelDonRepository appels;
    @Autowired
    CompensationRepository compensations;

    private Demande nouvelleDemande() {
        return demandes.save(Demande.builder()
                .hopitalId("HOP-SFX-01")
                .hopitalNom("CHU Habib Bourguiba Sfax")
                .patientRef("PAT1")
                .motif(MotifDemande.CHIRURGIE)
                .groupeSanguin(GroupeSanguin.O_NEG)
                .composant(Composant.CGR)
                .quantitePoches(3)
                .urgence(Urgence.HAUTE)
                .statut(StatutDemande.EN_ATTENTE)
                .build());
    }

    @Test
    void uneDemandeEstEnregistreeAvecSesValeursParDefaut() {
        Demande d = nouvelleDemande();

        Demande relue = demandes.findById(d.getId()).orElseThrow();
        assertThat(relue.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE);
        assertThat(relue.getCompensationRecue()).isZero();
        assertThat(relue.getDateCreation()).isNotNull();
    }

    @Test
    void unAppelPeutExisterSansDemande() {
        AppelDon appel = appels.save(AppelDon.builder()
                .groupeSanguin(GroupeSanguin.AB_NEG)
                .composant(Composant.PLAQUETTES)
                .statut(StatutAppelDon.OUVERT)
                .build());

        assertThat(appels.findById(appel.getId()).orElseThrow().getDateOuverture()).isNotNull();
    }

    @Test
    void unNumeroDeCertificatNeCompteQuUneFois() {
        Demande d = nouvelleDemande();
        compensations.saveAndFlush(Compensation.builder().demande(d).numeroCertificat("CERT-2026-000001").build());

        Compensation doublon = Compensation.builder().demande(d).numeroCertificat("CERT-2026-000001").build();

        assertThatThrownBy(() -> compensations.saveAndFlush(doublon))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
