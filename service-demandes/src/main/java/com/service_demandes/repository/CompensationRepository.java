package com.service_demandes.repository;

import com.service_demandes.entity.Compensation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompensationRepository extends JpaRepository<Compensation, UUID> {
}
