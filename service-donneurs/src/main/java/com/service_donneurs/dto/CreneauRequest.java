package com.service_donneurs.dto;

import java.time.LocalDateTime;

public record CreneauRequest(

        Long centreId,

        LocalDateTime debut,

        LocalDateTime fin,

        Integer capacite

) {
}