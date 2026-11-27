package com.service_donneurs.service;

import com.service_donneurs.dto.CentreRequest;
import com.service_donneurs.dto.CentreResponse;
import com.service_donneurs.model.Centre;
import com.service_donneurs.repository.CentreRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CentreService {

    private final CentreRepository centreRepository;

    public CentreService(CentreRepository centreRepository) {
        this.centreRepository = centreRepository;
    }

    public List<CentreResponse> getAll() {
        return centreRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public CentreResponse getById(Long id) {
        return toResponse(centreRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Centre introuvable")));
    }

    public CentreResponse create(CentreRequest request) {
        Centre centre = toEntity(request);
        return toResponse(centreRepository.save(centre));
    }

    public CentreResponse update(Long id, CentreRequest request) {

        Centre existing = centreRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Centre introuvable"));

        existing.setNom(request.nom());
        existing.setType(request.type());
        existing.setVille(request.ville());
        existing.setDateDebut(request.dateDebut());
        existing.setDateFin(request.dateFin());

        return toResponse(centreRepository.save(existing));
    }

    public void delete(Long id) {
        centreRepository.deleteById(id);
    }

    private Centre toEntity(CentreRequest request) {
        Centre centre = new Centre();
        centre.setNom(request.nom());
        centre.setType(request.type());
        centre.setVille(request.ville());
        centre.setDateDebut(request.dateDebut());
        centre.setDateFin(request.dateFin());
        return centre;
    }

    private CentreResponse toResponse(Centre centre) {
        return new CentreResponse(
                centre.getId(),
                centre.getNom(),
                centre.getType(),
                centre.getVille(),
                centre.getDateDebut(),
                centre.getDateFin());
    }
}