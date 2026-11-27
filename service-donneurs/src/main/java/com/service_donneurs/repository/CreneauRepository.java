package com.service_donneurs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.service_donneurs.model.Creneau;

public interface CreneauRepository
        extends JpaRepository<Creneau, Long> {

    List<Creneau> findByCentreId(Long centreId);
}