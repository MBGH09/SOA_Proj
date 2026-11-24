package com.service_donneurs.controller;

import com.service_donneurs.dto.EligibilityResponse;
import com.service_donneurs.service.EligibilityService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/eligibility")
public class EligibilityController {

    private final EligibilityService eligibilityService;

    public EligibilityController(
            EligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
    }

    @GetMapping("/{cin}")
    public EligibilityResponse verifier(
            @PathVariable String cin) {

        return eligibilityService.verifierParCin(cin);
    }
}