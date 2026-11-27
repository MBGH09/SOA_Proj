package tn.iit.blood.service_don_stock.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.iit.blood.service_don_stock.model.StockNiveau;
import tn.iit.blood.service_don_stock.model.StockNiveauId;

import java.util.List;
import java.util.Optional;

public interface StockNiveauRepository extends JpaRepository<StockNiveau, StockNiveauId> {

    List<StockNiveau> findByLieuId(String lieuId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockNiveau s WHERE s.lieuId = :lieuId AND s.groupeSanguin = :groupe AND s.composant = :composant")
    Optional<StockNiveau> findByKeyForUpdate(
        @Param("lieuId")    String lieuId,
        @Param("groupe")    String groupeSanguin,
        @Param("composant") String composant
    );

    @Query("SELECT s FROM StockNiveau s WHERE (:lieuId IS NULL OR s.lieuId = :lieuId) AND (:groupe IS NULL OR s.groupeSanguin = :groupe)")
    List<StockNiveau> findByFilters(
        @Param("lieuId") String lieuId,
        @Param("groupe") String groupeSanguin
    );
}
