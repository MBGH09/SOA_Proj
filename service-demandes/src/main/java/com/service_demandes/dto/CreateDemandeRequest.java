package com.service_demandes.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDemandeRequest(

        @NotBlank(message = "L'identifiant de l'hôpital est obligatoire")
        String hopitalId,

        @NotBlank(message = "Le nom de l'hôpital est obligatoire")
        String hopitalNom,

        @NotBlank(message = "La référence du patient est obligatoire")
        String patientRef,

        String motif,

        @NotBlank(message = "Le groupe sanguin est obligatoire")
        String groupeSanguin,

        @NotBlank(message = "Le composant est obligatoire")
        String composant,

        @NotNull(message = "La quantité est obligatoire")
        @Min(value = 1, message = "La quantité doit être supérieure à 0")
        Integer quantitePoches,

        @NotBlank(message = "Le niveau d'urgence est obligatoire")
        String urgence
) {}