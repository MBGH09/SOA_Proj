package com.service_demandes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corps de POST /api/v1/demandes/{id}/compensations. */
public record CreateCompensationRequest(

        @NotBlank(message = "Le numéro de certificat est obligatoire")
        @Size(max = 100, message = "Le numéro de certificat est trop long (100 caractères au maximum)")
        String numeroCertificat
) {
}
