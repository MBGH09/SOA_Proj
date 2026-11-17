package com.service_donneurs.dto;

import java.time.LocalDate;

public record DonneurResponse(
        Long id,
        String cin,
        String nom,
        String prenom,
        LocalDate dateNaissance,
        Integer poidsKg,
        String groupeSanguin,
        String ville,
        String telephone,
        Boolean premierDon
) {
}