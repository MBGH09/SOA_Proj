package com.service_donneurs.repository;

import com.service_donneurs.model.Donneur;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DonneurRepository
        extends JpaRepository<Donneur, Long> {
                Optional<Donneur> findByCin(String cin);


}