package com.service_demandes.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.service_demandes.entity.Compensation;

import java.util.UUID;

public interface CompensationRepository
        extends JpaRepository<Compensation, UUID> {
}