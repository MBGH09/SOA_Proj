package com.service_demandes.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Faux Don & Stock pour développer sans attendre l'Étudiant 4. Actif avec le profil {@code mock}.
 * Mode configurable via {@code donstock.mock.mode} : LOCAL, COMMANDE, PENURIE, INDISPONIBLE.
 * <p>En mode COMMANDE, {@link #consulterReservation(UUID)} répond LIVREE pour simuler
 * l'arrivée de la livraison (utile pour tester le job de réconciliation).</p>
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
            case LOCAL       -> new ReservationResultat(StatutReservation.RESERVEE_STOCK_LOCAL, reference, null, null);
            case COMMANDE    -> new ReservationResultat(StatutReservation.APPROVISIONNEMENT_COMMANDE, reference, "CRTS Sfax", 120);
            case PENURIE     -> new ReservationResultat(StatutReservation.STOCK_INSUFFISANT_PARTOUT, null, null, null);
            case INDISPONIBLE -> throw new DonStockIndisponibleException("Don & Stock simulé indisponible");
        };
    }

    @Override
    public void libererReservation(UUID demandeId, String reference) {
        // Pas d'état persistant dans le mock.
    }

    @Override
    public Optional<ConsultationResultat> consulterReservation(UUID demandeId) {
        String demandeIdStr = demandeId.toString();
        return switch (mode) {
            case LOCAL       -> Optional.of(new ConsultationResultat(demandeIdStr, StatutReservation.RESERVEE_STOCK_LOCAL, "MOCK-REF", null, null));
            case COMMANDE    -> Optional.of(new ConsultationResultat(demandeIdStr, StatutReservation.LIVREE, "MOCK-REF", "CRTS Sfax", 0));
            case PENURIE     -> Optional.of(new ConsultationResultat(demandeIdStr, StatutReservation.STOCK_INSUFFISANT_PARTOUT, null, null, null));
            case INDISPONIBLE -> throw new DonStockIndisponibleException("Don & Stock simulé indisponible");
        };
    }
}
