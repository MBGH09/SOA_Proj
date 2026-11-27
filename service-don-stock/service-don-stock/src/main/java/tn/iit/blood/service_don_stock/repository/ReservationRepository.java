package tn.iit.blood.service_don_stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iit.blood.service_don_stock.model.Reservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByDemandeId(String demandeId);

    List<Reservation> findByStatut(String statut);

    List<Reservation> findByStatutAndCreatedAtBefore(String statut, Instant before);
}
