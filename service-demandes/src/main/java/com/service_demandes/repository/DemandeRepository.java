package com.service_demandes.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.service_demandes.entity.Demande;

import java.util.UUID;

public interface DemandeRepository
        extends JpaRepository<Demande, UUID> {
}