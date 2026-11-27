package com.service_donneurs.service;

import com.service_donneurs.dto.DonneurRequest;
import com.service_donneurs.dto.DonneurResponse;
import com.service_donneurs.model.Donneur;
import com.service_donneurs.repository.DonneurRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class DonneurService {

    private final DonneurRepository donneurRepository;

    public DonneurService(DonneurRepository donneurRepository) {
        this.donneurRepository = donneurRepository;
    }

    public List<DonneurResponse> getAll() {
        return donneurRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public DonneurResponse getById(Long id) {
        return toResponse(donneurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Donneur introuvable")));
    }

public DonneurResponse create(DonneurRequest request) {

    if (request == null
            || request.cin() == null
            || !request.cin().matches("\\d{8}")
            || request.nom() == null
            || request.nom().isBlank()
            || request.prenom() == null
            || request.prenom().isBlank()) {

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Donneur invalide");
    }

    if (donneurRepository.findByCin(request.cin()).isPresent()) {

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "CIN déjà existant");
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
    donneur.setPremierDon(request.premierDon());

    return toResponse(
            donneurRepository.save(donneur));
}
    public DonneurResponse update(Long id, DonneurRequest request) {

        Donneur donneur = donneurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Donneur introuvable"));

        donneur.setNom(request.nom());
        donneur.setPrenom(request.prenom());
        donneur.setPoidsKg(request.poidsKg());
        donneur.setVille(request.ville());
        donneur.setTelephone(request.telephone());

        return toResponse(donneurRepository.save(donneur));
    }

    public void delete(Long id) {
        donneurRepository.deleteById(id);
    }

    private DonneurResponse toResponse(Donneur donneur) {
        return new DonneurResponse(
                donneur.getId(),
                donneur.getCin(),
                donneur.getNom(),
                donneur.getPrenom(),
                donneur.getDateNaissance(),
                donneur.getPoidsKg(),
                donneur.getGroupeSanguin(),
                donneur.getVille(),
                donneur.getTelephone(),
                donneur.getPremierDon());
    }

}