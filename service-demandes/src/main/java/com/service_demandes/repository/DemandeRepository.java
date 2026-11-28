package com.service_demandes.repository;

import com.service_demandes.entity.Demande;
import com.service_demandes.entity.StatutDemande;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandeRepository extends JpaRepository<Demande, UUID>, JpaSpecificationExecutor<Demande> {

    /** Verrou pessimiste en écriture pour les mises à jour critiques (annulation, appliquer résultat). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Demande d where d.id = :id")
    Optional<Demande> findByIdVerrouillee(@Param("id") UUID id);

    /** Toutes les demandes dans un statut donné (utilisé par le job de réconciliation). */
    List<Demande> findByStatutIn(List<StatutDemande> statuts);
}
