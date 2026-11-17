package com.service_demandes.dto;

import com.service_demandes.entity.Composant;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.MotifDemande;
import com.service_demandes.entity.Urgence;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corps de POST /api/v1/demandes (schema DemandeRequest du contrat OpenAPI).
 * Un groupe, un composant, un motif ou une urgence inconnu est refuse a la lecture du JSON (400).
 */
public record CreateDemandeRequest(

        @NotBlank(message = "L'identifiant de l'hôpital est obligatoire")
        @Size(max = 100, message = "L'identifiant de l'hôpital est trop long (100 caractères au maximum)")
        String hopitalId,

        @NotBlank(message = "Le nom de l'hôpital est obligatoire")
        @Size(max = 255, message = "Le nom de l'hôpital est trop long (255 caractères au maximum)")
        String hopitalNom,

        // R19 : référence anonyme, jamais le nom du patient
        @NotBlank(message = "La référence du patient est obligatoire")
        @Size(max = 50, message = "La référence du patient est trop longue (50 caractères au maximum)")
        @Pattern(regexp = "^[A-Za-z0-9]*$", message = "La référence du patient ne contient que des lettres et des chiffres")
        String patientRef,

        @NotNull(message = "Le motif est obligatoire")
        MotifDemande motif,

        @NotNull(message = "Le groupe sanguin est obligatoire")
        GroupeSanguin groupeSanguin,

        @NotNull(message = "Le composant est obligatoire")
        Composant composant,

        // R11
        @NotNull(message = "La quantité est obligatoire")
        @Min(value = 1, message = "La quantité doit être supérieure à 0")
        Integer quantitePoches,

        @NotNull(message = "Le niveau d'urgence est obligatoire")
        Urgence urgence,

        // R17 : 2 à 3 donneurs de la famille, facultatif
        @Min(value = 2, message = "La compensation demandée est de 2 donneurs au minimum")
        @Max(value = 3, message = "La compensation demandée est de 3 donneurs au maximum")
        Integer compensationDemandee
) {
}
