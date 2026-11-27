package tn.iit.blood.cnts.repository;

import tn.iit.blood.cnts.entity.StockCentral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockCentralRepository extends JpaRepository<StockCentral, Long> {

    /**
     * Atomic stock decrement: only succeeds if stock >= requested quantity.
     * Returns 1 if updated, 0 if stock insufficient (R13/R14).
     */
    /**
     * All-or-nothing decrement (S5-06). The WHERE clause refuses the update
     * when the row cannot cover the whole quantity, so a failed attempt
     * leaves the stock unchanged. flushAutomatically writes any stock row
     * inserted in this transaction before the UPDATE. clearAutomatically
     * drops the cached entity so a later read sees the new quantity.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE StockCentral s
        SET s.quantite = s.quantite - :quantite, s.miseAJour = CURRENT_TIMESTAMP
        WHERE s.centreId = :centreId
          AND s.groupeSanguin = :groupeSanguin
          AND s.composant = :composant
          AND s.quantite >= :quantite
        """)
    int decrementStock(@Param("centreId") String centreId,
                       @Param("groupeSanguin") String groupeSanguin,
                       @Param("composant") String composant,
                       @Param("quantite") int quantite);

    List<StockCentral> findByCentreIdStartingWith(String regionPrefix);

    Optional<StockCentral> findByCentreIdAndGroupeSanguinAndComposant(
            String centreId, String groupeSanguin, String composant);
}
