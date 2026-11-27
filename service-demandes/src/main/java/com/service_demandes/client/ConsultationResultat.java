package com.service_demandes.client;

/**
 * Réponse de {@code ConsulterReservation} (gRPC {@code ConsulterReservationResponse}).
 */
public record ConsultationResultat(
        String demandeId,
        StatutReservation statut,
        String reference,
        String centreSource,
        Integer etaMinutes
) {}
