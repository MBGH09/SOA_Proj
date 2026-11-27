package com.service_donneurs.controller;

import com.service_donneurs.dto.CreneauRequest;
import com.service_donneurs.dto.CreneauResponse;
import com.service_donneurs.service.CreneauService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CreneauController {

    private final CreneauService creneauService;

    public CreneauController(CreneauService creneauService) {
        this.creneauService = creneauService;
    }

    @GetMapping("/api/creneaux")
    public List<CreneauResponse> getAll() {
        return creneauService.getAll();
    }

    @GetMapping("/api/centres/{id}/creneaux")
    public List<CreneauResponse> getByCentre(
            @PathVariable Long id) {

        return creneauService.getByCentre(id);
    }

    @PostMapping("/api/creneaux")
    public CreneauResponse create(
            @RequestBody CreneauRequest request) {

        return creneauService.create(request);
    }

    @DeleteMapping("/api/creneaux/{id}")
    public void delete(
            @PathVariable Long id) {

        creneauService.delete(id);
    }
}