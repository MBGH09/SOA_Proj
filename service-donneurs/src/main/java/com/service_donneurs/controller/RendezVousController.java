package com.service_donneurs.controller;

import com.service_donneurs.dto.RendezVousRequest;
import com.service_donneurs.dto.RendezVousResponse;
import com.service_donneurs.service.RendezVousService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rendez-vous")
public class RendezVousController {

    private final RendezVousService rendezVousService;

    public RendezVousController(
            RendezVousService rendezVousService) {

        this.rendezVousService = rendezVousService;
    }

    @GetMapping
    public List<RendezVousResponse> getAll() {

        return rendezVousService.getAll();
    }

    @GetMapping("/{id}")
    public RendezVousResponse getById(
            @PathVariable Long id) {

        return rendezVousService.getById(id);
    }

    @GetMapping("/donneur/{id}")
    public List<RendezVousResponse> getByDonneur(
            @PathVariable Long id) {

        return rendezVousService.getByDonneur(id);
    }

    @PostMapping
    public RendezVousResponse create(
            @RequestBody RendezVousRequest request) {

        return rendezVousService.create(request);
    }

    @PutMapping("/{id}/annuler")
    public RendezVousResponse annuler(
            @PathVariable Long id) {

        return rendezVousService.annuler(id);
    }

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable Long id) {

        rendezVousService.delete(id);
    }
}