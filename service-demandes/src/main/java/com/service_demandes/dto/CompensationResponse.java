package com.service_demandes.dto;

import com.service_demandes.entity.Compensation;

import java.time.Instant;
import java.util.UUID;

/** Schema Compensation du contrat OpenAPI. */
public record CompensationResponse(
        UUID id,
        UUID demandeId,
        String numeroCertificat,
        Instant dateEnregistrement,
        int compensationRecue
) {

    /** @param compensationRecue compteur de la demande apres cet enregistrement */
    public static CompensationResponse from(Compensation c, int compensationRecue) {
        return new CompensationResponse(
                c.getId(),
                c.getDemande().getId(),
                c.getNumeroCertificat(),
                c.getDateEnregistrement(),
                compensationRecue);
    }
}
