package com.service_demandes.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Faux Don & Stock pour developper sans attendre l'Etudiant 4. Actif avec le profil "mock".
 * Le comportement se choisit avec donstock.mock.mode (LOCAL, COMMANDE, PENURIE ou INDISPONIBLE).
 * Le faux serveur gRPC complet de S6-03 le remplacera.
 */
@Component
@Profile("mock")
public class DonStockClientMock implements DonStockClient {

    public enum Mode { LOCAL, COMMANDE, PENURIE, INDISPONIBLE }

    private final Mode mode;

    public DonStockClientMock(@Value("${donstock.mock.mode:LOCAL}") Mode mode) {
        this.mode = mode;
    }

    @Override
    public ReservationResultat verifierEtReserver(ReservationCommande commande) {
        String reference = "MOCK-" + commande.demandeId().toString().substring(0, 8);
        return switch (mode) {
            case LOCAL -> new ReservationResultat(StatutReservation.RESERVEE_STOCK_LOCAL, reference, null, null);
            case COMMANDE -> new ReservationResultat(StatutReservation.APPROVISIONNEMENT_COMMANDE, reference, "CRTS Sfax", 120);
            case PENURIE -> new ReservationResultat(StatutReservation.STOCK_INSUFFISANT_PARTOUT, null, null, null);
            case INDISPONIBLE -> throw new DonStockIndisponibleException("Don & Stock simulé indisponible");
        };
    }
}
