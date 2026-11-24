package com.service_demandes.dto;

import java.time.Instant;

/** Format d'erreur unique de l'API : timestamp, status, error, message, path. */
public record ErreurApi(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
