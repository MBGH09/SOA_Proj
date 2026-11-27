package com.service_donneurs.service;

import com.service_donneurs.dto.CreneauRequest;
import com.service_donneurs.dto.CreneauResponse;
import com.service_donneurs.model.Centre;
import com.service_donneurs.model.Creneau;
import com.service_donneurs.repository.CentreRepository;
import com.service_donneurs.repository.CreneauRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CreneauService {

    private final CreneauRepository creneauRepository;
    private final CentreRepository centreRepository;

    public CreneauService(
            CreneauRepository creneauRepository,
            CentreRepository centreRepository) {
        this.creneauRepository = creneauRepository;
        this.centreRepository = centreRepository;
    }

    public List<CreneauResponse> getAll() {
        return creneauRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<CreneauResponse> getByCentre(Long centreId) {
        return creneauRepository.findByCentreId(centreId).stream()
                .map(this::toResponse)
                .toList();
    }

    public CreneauResponse create(CreneauRequest request) {
        Centre centre = centreRepository.findById(request.centreId())
                .orElseThrow(() -> new RuntimeException("Centre introuvable"));

        Creneau creneau = new Creneau();
        creneau.setCentre(centre);
        creneau.setDebut(request.debut());
        creneau.setFin(request.fin());
        creneau.setCapacite(request.capacite());

        return toResponse(creneauRepository.save(creneau));
    }

    public void delete(Long id) {
        creneauRepository.deleteById(id);
    }

    private CreneauResponse toResponse(Creneau creneau) {
        return new CreneauResponse(
                creneau.getId(),
                creneau.getCentre() == null ? null : creneau.getCentre().getId(),
                creneau.getDebut(),
                creneau.getFin(),
                creneau.getCapacite());
    }
}