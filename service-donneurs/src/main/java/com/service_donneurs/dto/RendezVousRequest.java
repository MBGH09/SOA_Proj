package com.service_donneurs.dto;

public record RendezVousRequest(
        Long donneurId,
        Long centreId,
        Long creneauId,
        String canal
) {
}