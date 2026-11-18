package com.service_donneurs.service;

import com.service_donneurs.dto.EligibilityResponse;
import com.service_donneurs.model.Donneur;
import com.service_donneurs.repository.DonneurRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;

@Service
public class EligibilityService {

        private final DonneurRepository donneurRepository;

    public EligibilityService(DonneurRepository donneurRepository) {
        this.donneurRepository = donneurRepository;
    }

    // nouvelle méthode ici

    public EligibilityResponse verifier(Donneur donneur,
                                        boolean ajournementActif) {

        int age = Period.between(
                donneur.getDateNaissance(),
                LocalDate.now())
                .getYears();

        if (age < 18) {
            return new EligibilityResponse(false,
                    "AGE_MIN_NON_ATTEINT");
        }

        if (age > 70) {
            return new EligibilityResponse(false,
                    "AGE_MAX_DEPASSE");
        }

        if (Boolean.TRUE.equals(donneur.getPremierDon())
                && age > 65) {
            return new EligibilityResponse(false,
                    "PREMIER_DON_AGE_DEPASSE");
        }

        if (donneur.getPoidsKg() < 50) {
            return new EligibilityResponse(false,
                    "POIDS_INSUFFISANT");
        }

        if (ajournementActif) {
            return new EligibilityResponse(false,
                    "AJOURNEMENT_ACTIF");
        }

        return new EligibilityResponse(true,
                "ELIGIBLE");
    }


    public EligibilityResponse verifierParCin(String cin) {

    Donneur donneur = donneurRepository.findByCin(cin)
            .orElseThrow(() ->
                    new RuntimeException("Donneur introuvable"));

    boolean ajournementActif = false;

    return verifier(donneur, ajournementActif);
}
}