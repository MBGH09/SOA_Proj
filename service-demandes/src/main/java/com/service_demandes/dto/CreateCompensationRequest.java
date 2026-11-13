package com.service_demandes.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCompensationRequest(

        @NotBlank(message = "Le numéro de certificat est obligatoire")
        String numeroCertificat
) {}