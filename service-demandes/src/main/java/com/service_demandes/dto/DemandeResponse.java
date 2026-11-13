package com.service_demandes.dto;

import com.service_demandes.entity.Composant;
import com.service_demandes.entity.Demande;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.MotifDemande;
import com.service_demandes.entity.StatutDemande;
import com.service_demandes.entity.Urgence;

import java.time.Instant;
import java.util.UUID;

/** Schema Demande du contrat OpenAPI. */
public record DemandeResponse(
        UUID id,
        String hopitalId,
        String hopitalNom,
        String patientRef,
        MotifDemande motif,
        GroupeSanguin groupeSanguin,
        Composant composant,
        int quantitePoches,
        Urgence urgence,
        StatutDemande statut,
        String referenceApprovisionnement,
        Integer compensationDemandee,
        int compensationRecue,
        Instant dateCreation
) {

    public static DemandeResponse from(Demande d) {
        return new DemandeResponse(
                d.getId(),
                d.getHopitalId(),
                d.getHopitalNom(),
                d.getPatientRef(),
                d.getMotif(),
                d.getGroupeSanguin(),
                d.getComposant(),
                d.getQuantitePoches(),
                d.getUrgence(),
                d.getStatut(),
                d.getReferenceApprovisionnement(),
                d.getCompensationDemandee(),
                d.getCompensationRecue(),
                d.getDateCreation());
    }
}
