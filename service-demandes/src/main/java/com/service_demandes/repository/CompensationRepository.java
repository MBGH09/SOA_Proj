package com.service_demandes.repository;

import com.service_demandes.entity.Compensation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompensationRepository extends JpaRepository<Compensation, UUID> {

    /** Toutes les compensations d'une demande. */
    List<Compensation> findByDemandeId(UUID demandeId);

    /** Vérifie si un numéro est déjà utilisé (R26). */
    Optional<Compensation> findByNumeroCertificat(String numeroCertificat);

    /** Compte les compensations valides d'une demande pour recalculer le compteur. */
    long countByDemandeId(UUID demandeId);
}
