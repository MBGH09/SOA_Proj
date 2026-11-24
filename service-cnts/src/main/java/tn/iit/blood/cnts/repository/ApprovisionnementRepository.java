package tn.iit.blood.cnts.repository;

import tn.iit.blood.cnts.entity.Approvisionnement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ApprovisionnementRepository extends JpaRepository<Approvisionnement, Long> {

    Optional<Approvisionnement> findByDemandeId(String demandeId);

    /** Next value of seq_approvisionnement (V1__init.sql / schema-h2.sql). */
    @Query(value = "SELECT nextval('seq_approvisionnement')", nativeQuery = true)
    Long nextSequenceValue();
}
