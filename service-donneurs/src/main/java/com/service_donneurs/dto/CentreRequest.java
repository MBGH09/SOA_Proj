package com.service_donneurs.dto;

import java.time.LocalDate;

public record CentreRequest(

        String nom,

        String type,

        String ville,

        LocalDate dateDebut,

        LocalDate dateFin

) {
}