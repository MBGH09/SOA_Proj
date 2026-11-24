package com.service_donneurs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.service_donneurs.dto.DonneurRequest;
import com.service_donneurs.dto.DonneurResponse;
import com.service_donneurs.model.Donneur;
import com.service_donneurs.service.DonneurService;

@RestController
@RequestMapping({"/api/v1/donneurs", "/api/donneurs"})
public class DonneurController {

    private final DonneurService donneurService;

    public DonneurController(DonneurService donneurService) {
        this.donneurService = donneurService;
    }

    @GetMapping
    public List<Donneur> getAll() {
        return donneurService.findAll();
    }

    @PostMapping
    public ResponseEntity<DonneurResponse> creer(@RequestBody DonneurRequest request) {
        Donneur saved = donneurService.creer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DonneurResponse.from(saved));
    }
}
