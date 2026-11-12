package com.service_demandes;

import com.service_demandes.client.DonStockClient;
import com.service_demandes.client.ReservationCommande;
import com.service_demandes.client.ReservationResultat;
import com.service_demandes.client.StatutReservation;
import com.service_demandes.dto.CreateDemandeRequest;
import com.service_demandes.entity.Composant;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.MotifDemande;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.entity.Urgence;
import com.service_demandes.repository.DemandeRepository;
import com.service_demandes.service.DemandeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Verifie la regle des deux transactions : rien d'ouvert pendant l'appel a Don & Stock. */
@SpringBootTest
@ActiveProfiles("test")
class DemandeServiceTest {

    @Autowired
    DemandeService service;
    @Autowired
    DemandeRepository demandes;
    @MockitoBean
    DonStockClient donStock;

    private CreateDemandeRequest requete() {
        return new CreateDemandeRequest("HOP-SFX-01", "CHU Habib Bourguiba Sfax", "PAT7",
                MotifDemande.URGENCE, GroupeSanguin.A_POS, Composant.CGR, 2, Urgence.CRITIQUE, null);
    }

    @Test
    void pendantLappelAuStock_aucuneTransactionNestOuverte_et_la_demande_est_deja_validee_en_base() {
        when(donStock.verifierEtReserver(any())).thenAnswer(invocation -> {
            ReservationCommande commande = invocation.getArgument(0);

            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("aucune transaction ne doit etre ouverte pendant l'appel gRPC")
                    .isFalse();
            Demande enBase = demandes.findById(commande.demandeId()).orElseThrow();
            assertThat(enBase.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE);

            return new ReservationResultat(StatutReservation.RESERVEE_STOCK_LOCAL, "RES-9", null, null);
        });

        Demande resultat = service.creer(requete());

        assertThat(resultat.getStatut()).isEqualTo(StatutDemande.SATISFAITE);
        assertThat(demandes.findById(resultat.getId()).orElseThrow().getReferenceApprovisionnement()).isEqualTo("RES-9");
    }

    @Test
    void uneDemandeAnnuleePendantLappelNestPasEcrasee() {
        when(donStock.verifierEtReserver(any())).thenAnswer(invocation -> {
            ReservationCommande commande = invocation.getArgument(0);
            Demande d = demandes.findById(commande.demandeId()).orElseThrow();
            d.setStatut(StatutDemande.ANNULEE);
            demandes.save(d);
            return new ReservationResultat(StatutReservation.RESERVEE_STOCK_LOCAL, "RES-10", null, null);
        });

        Demande resultat = service.creer(requete());

        assertThat(resultat.getStatut()).isEqualTo(StatutDemande.ANNULEE);
        assertThat(demandes.findById(resultat.getId()).orElseThrow().getStatut()).isEqualTo(StatutDemande.ANNULEE);
    }
}
