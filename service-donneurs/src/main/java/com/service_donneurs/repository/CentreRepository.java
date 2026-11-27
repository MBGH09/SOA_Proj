package com.service_donneurs.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.service_donneurs.model.Centre;

public interface CentreRepository
        extends JpaRepository<Centre, Long> {
}