package tn.iit.blood.service_don_stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iit.blood.service_don_stock.model.Prelevement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PrelevementRepository extends JpaRepository<Prelevement, Long> {

    Optional<Prelevement> findByDonId(String donId);

    List<Prelevement> findByStatut(String statut);

    List<Prelevement> findByStatutAndCreatedAtBefore(String statut, Instant before);
}
