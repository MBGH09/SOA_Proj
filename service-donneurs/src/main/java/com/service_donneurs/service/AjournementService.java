package com.service_donneurs.service;

import com.service_donneurs.dto.AjournementRequest;
import com.service_donneurs.dto.AjournementResponse;
import com.service_donneurs.model.Ajournement;
import com.service_donneurs.model.Donneur;
import com.service_donneurs.repository.AjournementRepository;
import com.service_donneurs.repository.DonneurRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AjournementService {

    private final AjournementRepository ajournementRepository;
    private final DonneurRepository donneurRepository;

    public AjournementService(
            AjournementRepository ajournementRepository,
            DonneurRepository donneurRepository) {

        this.ajournementRepository = ajournementRepository;
        this.donneurRepository = donneurRepository;
    }

    public List<AjournementResponse> getAll() {
        return ajournementRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AjournementResponse> getByDonneur(
            Long donneurId) {

        return ajournementRepository
                .findByDonneurId(donneurId).stream()
                .map(this::toResponse)
                .toList();
    }

    public AjournementResponse create(AjournementRequest request) {
        Donneur donneur = donneurRepository.findById(request.donneurId())
                .orElseThrow(() -> new RuntimeException("Donneur introuvable"));

        Ajournement ajournement = new Ajournement();
        ajournement.setDonneur(donneur);
        ajournement.setMotif(request.motif());
        ajournement.setDateDebut(request.dateDebut());
        ajournement.setDateFin(request.dateFin());

        return toResponse(ajournementRepository.save(ajournement));
    }

    public void delete(Long id) {
        ajournementRepository.deleteById(id);
    }

    private AjournementResponse toResponse(Ajournement ajournement) {
        return new AjournementResponse(
                ajournement.getId(),
                ajournement.getDonneur() == null ? null : ajournement.getDonneur().getId(),
                ajournement.getMotif(),
                ajournement.getDateDebut(),
                ajournement.getDateFin());
    }
}