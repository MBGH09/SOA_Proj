package com.service_demandes.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DemandeResponse(

        UUID id,

        String hopitalId,

        String hopitalNom,

        String patientRef,

        String motif,

        String groupeSanguin,

        String composant,

        Integer quantitePoches,

        String urgence,

        String statut,

        Integer compensationDemandee,

        Integer compensationRecue,

        LocalDateTime dateCreation
) {}