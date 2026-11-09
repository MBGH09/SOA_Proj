package com.service_demandes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAppelDonRequest(

        @NotNull(message = "L'identifiant de la demande est obligatoire")
        UUID demandeId,

        @NotBlank(message = "Le groupe sanguin est obligatoire")
        String groupeSanguin,

        @NotBlank(message = "Le composant est obligatoire")
        String composant
) {}