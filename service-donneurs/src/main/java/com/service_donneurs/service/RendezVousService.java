package com.service_donneurs.service;

import com.service_donneurs.dto.RendezVousRequest;
import com.service_donneurs.dto.RendezVousResponse;
import com.service_donneurs.model.*;
import com.service_donneurs.repository.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RendezVousService {

    private final RendezVousRepository rendezVousRepository;
    private final DonneurRepository donneurRepository;
    private final CentreRepository centreRepository;
    private final CreneauRepository creneauRepository;

    public RendezVousService(
            RendezVousRepository rendezVousRepository,
            DonneurRepository donneurRepository,
            CentreRepository centreRepository,
            CreneauRepository creneauRepository) {

        this.rendezVousRepository = rendezVousRepository;
        this.donneurRepository = donneurRepository;
        this.centreRepository = centreRepository;
        this.creneauRepository = creneauRepository;
    }

    public List<RendezVousResponse> getAll() {
        return rendezVousRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public RendezVousResponse getById(Long id) {

        return toResponse(rendezVousRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Rendez-vous introuvable")));
    }

    public List<RendezVousResponse> getByDonneur(Long donneurId) {

        return rendezVousRepository.findByDonneurId(donneurId).stream()
                .map(this::toResponse)
                .toList();
    }

    public RendezVousResponse create(RendezVousRequest request) {

        Donneur donneur = donneurRepository
                .findById(request.donneurId())
                .orElseThrow();

        Centre centre = centreRepository
                .findById(request.centreId())
                .orElseThrow();

        Creneau creneau = creneauRepository
                .findById(request.creneauId())
                .orElseThrow();

        RendezVous rdv = new RendezVous();

        rdv.setDonneur(donneur);
        rdv.setCentre(centre);
        rdv.setCreneau(creneau);

        rdv.setCanal(request.canal());
        rdv.setStatut("PLANIFIE");

        return toResponse(rendezVousRepository.save(rdv));
    }

    public RendezVousResponse annuler(Long id) {

        RendezVous rdv = rendezVousRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Rendez-vous introuvable"));

        rdv.setStatut("ANNULE");

        return toResponse(rendezVousRepository.save(rdv));
    }

    public void delete(Long id) {

        rendezVousRepository.deleteById(id);
    }

    private RendezVousResponse toResponse(RendezVous rendezVous) {
        return new RendezVousResponse(
                rendezVous.getId(),
                rendezVous.getDonneur() == null ? null : rendezVous.getDonneur().getId(),
                rendezVous.getCentre() == null ? null : rendezVous.getCentre().getId(),
                rendezVous.getCreneau() == null ? null : rendezVous.getCreneau().getId(),
                rendezVous.getCanal(),
                rendezVous.getStatut());
    }
}