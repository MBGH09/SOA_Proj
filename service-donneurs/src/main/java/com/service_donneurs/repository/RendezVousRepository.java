package com.service_donneurs.repository;

import com.service_donneurs.model.RendezVous;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RendezVousRepository
        extends JpaRepository<RendezVous, Long> {

    List<RendezVous> findByDonneurId(Long donneurId);
}