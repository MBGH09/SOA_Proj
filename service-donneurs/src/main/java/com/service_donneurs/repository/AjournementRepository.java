package com.service_donneurs.repository;

import com.service_donneurs.model.Ajournement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AjournementRepository
        extends JpaRepository<Ajournement, Long> {

}