package com.service_donneurs.dto;

import java.time.LocalDate;

public record AjournementRequest(

        Long donneurId,

        String motif,

        LocalDate dateDebut,

        LocalDate dateFin

) {
}