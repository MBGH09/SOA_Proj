package tn.iit.blood.service_don_stock.model;

import tn.iit.blood.donstock.v1.StatutReservation;

import java.util.Collection;
import java.util.List;

/**
 * Reservation status values as they are stored in the database.
 *
 * The protobuf enum is the source of truth.  A_CONFIRMER was used by the first
 * draft of the service; it is still accepted when reading older rows, but all
 * new rows and all responses use EN_ATTENTE_CONFIRMATION.
 */
public final class ReservationStatuts {

    public static final String EN_ATTENTE_CONFIRMATION = StatutReservation.EN_ATTENTE_CONFIRMATION.name();
    public static final String LEGACY_A_CONFIRMER = "A_CONFIRMER";

    /** Both spellings accepted by the retry job, so pre-migration rows are not lost. */
    public static final Collection<String> EN_ATTENTE_VALUES =
            List.of(EN_ATTENTE_CONFIRMATION, LEGACY_A_CONFIRMER);

    private ReservationStatuts() {}

    public static StatutReservation toProto(String statut) {
        if (LEGACY_A_CONFIRMER.equals(statut)) {
            return StatutReservation.EN_ATTENTE_CONFIRMATION;
        }
        if (statut == null) {
            return StatutReservation.STATUT_RESERVATION_UNSPECIFIED;
        }
        try {
            return StatutReservation.valueOf(statut);
        } catch (IllegalArgumentException e) {
            return StatutReservation.STATUT_RESERVATION_UNSPECIFIED;
        }
    }
}
