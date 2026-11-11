package com.service_demandes.dto;

import com.service_demandes.entity.AppelDon;
import com.service_demandes.entity.Composant;
import com.service_demandes.entity.GroupeSanguin;
import com.service_demandes.entity.StatutAppelDon;

import java.time.Instant;
import java.util.UUID;

/** Schema AppelDon du contrat OpenAPI. */
public record AppelDonResponse(
        UUID id,
        UUID demandeId,
        GroupeSanguin groupeSanguin,
        Composant composant,
        String centreCibleId,
        StatutAppelDon statut,
        Instant dateOuverture,
        Instant dateFermeture
) {

    public static AppelDonResponse from(AppelDon a) {
        return new AppelDonResponse(
                a.getId(),
                a.getDemande() == null ? null : a.getDemande().getId(),
                a.getGroupeSanguin(),
                a.getComposant(),
                a.getCentreCibleId(),
                a.getStatut(),
                a.getDateOuverture(),
                a.getDateFermeture());
    }
}
