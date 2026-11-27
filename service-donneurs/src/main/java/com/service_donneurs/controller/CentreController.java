package com.service_donneurs.controller;

import com.service_donneurs.dto.CentreRequest;
import com.service_donneurs.dto.CentreResponse;
import com.service_donneurs.service.CentreService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/centres")
public class CentreController {

    private final CentreService centreService;

    public CentreController(CentreService centreService) {
        this.centreService = centreService;
    }

    @GetMapping
    public List<CentreResponse> getAll() {
        return centreService.getAll();
    }

    @GetMapping("/{id}")
    public CentreResponse getById(@PathVariable Long id) {
        return centreService.getById(id);
    }

    @PostMapping
    public CentreResponse create(@RequestBody CentreRequest request) {
        return centreService.create(request);
    }

    @PutMapping("/{id}")
    public CentreResponse update(
            @PathVariable Long id,
            @RequestBody CentreRequest request) {

        return centreService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        centreService.delete(id);
    }
}