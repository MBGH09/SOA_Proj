package tn.iit.blood.cnts.repository;

import tn.iit.blood.cnts.entity.PrelevementDeclare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PrelevementDeclareRepository extends JpaRepository<PrelevementDeclare, Long> {

    Optional<PrelevementDeclare> findByDonIdLocal(String donIdLocal);

    Optional<PrelevementDeclare> findByNumeroDonNational(String numeroDonNational);

    /** Pulls the next value from the PostgreSQL sequence defined in V1__init.sql */
    @Query(value = "SELECT nextval('seq_don_national')", nativeQuery = true)
    Long nextSequenceValue();
}
