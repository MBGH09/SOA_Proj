package com.service_donneurs.service;

import com.service_donneurs.config.BloodRulesProperties;
import com.service_donneurs.dto.EligibilityResponse;
import com.service_donneurs.model.Donneur;
import com.service_donneurs.repository.DonneurRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;

@Service
public class EligibilityService {

        private final DonneurRepository donneurRepository;
        private final BloodRulesProperties rules;

    public EligibilityService(DonneurRepository donneurRepository, BloodRulesProperties rules) {
        this.donneurRepository = donneurRepository;
        this.rules = rules;
    }

    // nouvelle méthode ici

    public EligibilityResponse verifier(Donneur donneur,
                                        boolean ajournementActif) {

        int age = Period.between(
                donneur.getDateNaissance(),
                LocalDate.now())
                .getYears();

        if (age < rules.getAgeMin()) {
            return new EligibilityResponse(false,
                    "AGE_MIN_NON_ATTEINT");
        }

        if (age > rules.getAgeMax()) {
            return new EligibilityResponse(false,
                    "AGE_MAX_DEPASSE");
        }

        if (Boolean.TRUE.equals(donneur.getPremierDon())
                && age > rules.getAgePremierDonMax()) {
            return new EligibilityResponse(false,
                    "PREMIER_DON_AGE_DEPASSE");
        }

        if (donneur.getPoidsKg() < rules.getPoidsMinKg()) {
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