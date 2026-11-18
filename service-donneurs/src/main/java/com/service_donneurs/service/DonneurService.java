package com.service_donneurs.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.service_donneurs.model.Donneur;
import com.service_donneurs.repository.DonneurRepository;

@Service
public class DonneurService {

    private final DonneurRepository donneurRepository;

    public DonneurService(DonneurRepository donneurRepository) {
        this.donneurRepository = donneurRepository;
    }

    public List<Donneur> findAll() {
        return donneurRepository.findAll();
    }
}