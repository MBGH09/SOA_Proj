package tn.iit.blood.service_don_stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iit.blood.service_don_stock.model.Lieu;

import java.util.Optional;

public interface LieuRepository extends JpaRepository<Lieu, String> {

    Optional<Lieu> findByTypeAndRegion(String type, String region);
}
