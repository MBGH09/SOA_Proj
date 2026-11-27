package com.service_donneurs.dto;

import java.time.LocalDate;

public record AjournementResponse(

        Long id,

        Long donneurId,

        String motif,

        LocalDate dateDebut,

        LocalDate dateFin

) {
}