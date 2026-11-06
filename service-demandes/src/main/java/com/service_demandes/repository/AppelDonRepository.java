package com.service_demandes.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.service_demandes.entity.AppelDon;

import java.util.UUID;

public interface AppelDonRepository
        extends JpaRepository<AppelDon, UUID> {
}
