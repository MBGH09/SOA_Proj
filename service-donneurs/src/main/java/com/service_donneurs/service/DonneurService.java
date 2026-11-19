package com.service_donneurs.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.service_donneurs.dto.DonneurRequest;
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

    public Donneur creer(DonneurRequest request) {
        if (request == null
                || request.cin() == null
                || !request.cin().matches("\\d{8}")
                || request.nom() == null || request.nom().isBlank()
                || request.prenom() == null || request.prenom().isBlank()
                || request.dateNaissance() == null
                || request.poidsKg() == null
                || request.groupeSanguin() == null || request.groupeSanguin().isBlank()
                || request.ville() == null || request.ville().isBlank()
                || request.telephone() == null || request.telephone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donor fields are incomplete");
        }
        if (donneurRepository.findByCin(request.cin()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CIN already registered");
        }
        Donneur donneur = new Donneur();
        donneur.setCin(request.cin());
        donneur.setNom(request.nom());
        donneur.setPrenom(request.prenom());
        donneur.setDateNaissance(request.dateNaissance());
        donneur.setPoidsKg(request.poidsKg());
        donneur.setGroupeSanguin(request.groupeSanguin());
        donneur.setVille(request.ville());
        donneur.setTelephone(request.telephone());
        donneur.setPremierDon(request.premierDon() == null || request.premierDon());
        return donneurRepository.save(donneur);
    }
}