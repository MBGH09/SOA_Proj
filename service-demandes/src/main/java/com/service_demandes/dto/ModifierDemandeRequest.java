package com.service_demandes.dto;

import com.service_demandes.entity.StatutDemande;
import jakarta.validation.constraints.Min;

/**
 * Corps de PATCH /api/v1/demandes/{id} (schema DemandePatch). Au moins un champ.
 * statut : seule la valeur ANNULEE est acceptee. compensationRecue : correction manuelle du compteur.
 */
public record ModifierDemandeRequest(
        StatutDemande statut,

        @Min(value = 0, message = "Le compteur de compensation ne peut pas être négatif")
        Integer compensationRecue
) {
}
