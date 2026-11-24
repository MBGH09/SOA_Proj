package com.service_donneurs.dto;

import java.time.LocalDate;

import com.service_donneurs.model.Donneur;

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
    public static DonneurResponse from(Donneur donneur) {
        return new DonneurResponse(
                donneur.getId(),
                donneur.getCin(),
                donneur.getNom(),
                donneur.getPrenom(),
                donneur.getDateNaissance(),
                donneur.getPoidsKg(),
                donneur.getGroupeSanguin(),
                donneur.getVille(),
                donneur.getTelephone(),
                donneur.getPremierDon());
    }
}