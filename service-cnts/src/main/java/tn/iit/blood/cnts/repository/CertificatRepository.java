package tn.iit.blood.cnts.repository;

import tn.iit.blood.cnts.entity.Certificat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CertificatRepository extends JpaRepository<Certificat, Long> {

    Optional<Certificat> findByDonIdLocal(String donIdLocal);

    /** Pulls the next value from the PostgreSQL sequence defined in V1__init.sql */
    @Query(value = "SELECT nextval('seq_certificat')", nativeQuery = true)
    Long nextSequenceValue();
}
