package com.service_demandes.dto;

import com.service_demandes.entity.Composant;
import com.service_demandes.entity.GroupeSanguin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Corps de POST /api/v1/appels (schema AppelRequest). La demande et le centre sont facultatifs. */
public record CreateAppelDonRequest(

        @NotNull(message = "Le groupe sanguin est obligatoire")
        GroupeSanguin groupeSanguin,

        @NotNull(message = "Le composant est obligatoire")
        Composant composant,

        UUID demandeId,

        @Size(max = 100, message = "L'identifiant du centre est trop long (100 caractères au maximum)")
        String centreCibleId
) {
}
