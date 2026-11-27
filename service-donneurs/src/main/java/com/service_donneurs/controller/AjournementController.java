package com.service_donneurs.controller;

import com.service_donneurs.dto.AjournementRequest;
import com.service_donneurs.dto.AjournementResponse;
import com.service_donneurs.service.AjournementService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AjournementController {

    private final AjournementService ajournementService;

    public AjournementController(
            AjournementService ajournementService) {

        this.ajournementService = ajournementService;
    }

    @GetMapping("/api/ajournements")
    public List<AjournementResponse> getAll() {
        return ajournementService.getAll();
    }

    @GetMapping("/api/donneurs/{id}/ajournements")
    public List<AjournementResponse> getByDonneur(
            @PathVariable Long id) {

        return ajournementService
                .getByDonneur(id);
    }

    @PostMapping("/api/ajournements")
    public AjournementResponse create(
            @RequestBody AjournementRequest request) {

        return ajournementService
                .create(request);
    }

    @DeleteMapping("/api/ajournements/{id}")
    public void delete(
            @PathVariable Long id) {

        ajournementService.delete(id);
    }
}