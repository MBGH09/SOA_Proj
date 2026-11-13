package com.service_demandes.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CompensationResponse(

        UUID id,

        UUID demandeId,

        String numeroCertificat,

        LocalDateTime dateEnregistrement
) {}