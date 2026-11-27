package com.service_donneurs.dto;

public record RendezVousResponse(

        Long id,

        Long donneurId,

        Long centreId,

        Long creneauId,

        String canal,

        String statut

) {
}