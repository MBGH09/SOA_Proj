package com.service_donneurs.dto;

public record EligibilityResponse(
        boolean eligible,
        String motif
) {
}