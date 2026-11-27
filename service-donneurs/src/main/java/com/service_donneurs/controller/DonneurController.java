package com.service_donneurs.controller;

import com.service_donneurs.dto.DonneurRequest;
import com.service_donneurs.dto.DonneurResponse;
import com.service_donneurs.service.DonneurService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/donneurs")
public class DonneurController {

    private final DonneurService donneurService;

    public DonneurController(DonneurService donneurService) {
        this.donneurService = donneurService;
    }

    @GetMapping
    public List<DonneurResponse> getAll() {
        return donneurService.getAll();
    }

    @GetMapping("/{id}")
    public DonneurResponse getById(@PathVariable Long id) {
        return donneurService.getById(id);
    }

    @PostMapping
    public DonneurResponse create(
            @RequestBody DonneurRequest request) {

        return donneurService.create(request);
    }

    @PutMapping("/{id}")
    public DonneurResponse update(
            @PathVariable Long id,
            @RequestBody DonneurRequest request) {

        return donneurService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable Long id) {

        donneurService.delete(id);
    }
}