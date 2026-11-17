package com.service_donneurs.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.service_donneurs.model.Donneur;
import com.service_donneurs.service.DonneurService;

@RestController
@RequestMapping("/api/donneurs")
public class DonneurController {

    private final DonneurService donneurService;

    public DonneurController(
            DonneurService donneurService) {
        this.donneurService = donneurService;
    }

    @GetMapping
    public List<Donneur> getAll() {
        return donneurService.findAll();
    }
}