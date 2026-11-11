package com.service_demandes.client;

/** Ce que Don & Stock repond (ReservationResponse). centreSource et etaMinutes peuvent etre null. */
public record ReservationResultat(
        StatutReservation statut,
        String reference,
        String centreSource,
        Integer etaMinutes
) {
}
