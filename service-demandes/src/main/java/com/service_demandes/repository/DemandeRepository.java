package com.service_demandes.repository;

import com.service_demandes.entity.Demande;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DemandeRepository extends JpaRepository<Demande, UUID> {
}
