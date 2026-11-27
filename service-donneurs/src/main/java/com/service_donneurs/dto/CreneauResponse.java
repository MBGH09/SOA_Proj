package com.service_donneurs.dto;

import java.time.LocalDateTime;

public record CreneauResponse(

        Long id,

        Long centreId,

        LocalDateTime debut,

        LocalDateTime fin,

        Integer capacite

) {
}