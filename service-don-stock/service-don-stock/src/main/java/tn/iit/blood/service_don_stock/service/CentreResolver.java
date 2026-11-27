package tn.iit.blood.service_don_stock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.iit.blood.service_don_stock.model.Lieu;
import tn.iit.blood.service_don_stock.repository.LieuRepository;

import java.util.Optional;

/**
 * Resolves the place whose stock_niveau rows must be updated for a collection.
 *
 * A mobile collection has no stock of its own: S4-03/S4-07 require it to feed
 * the CRTS of its region.  Permanent places keep using their own stock rows.
 */
@Component
public class CentreResolver {

    public static final String TYPE_COLLECTE_MOBILE = "COLLECTE_MOBILE";
    public static final String TYPE_CRTS = "CRTS";

    private static final Logger log = LoggerFactory.getLogger(CentreResolver.class);

    private final LieuRepository lieuRepo;

    public CentreResolver(LieuRepository lieuRepo) {
        this.lieuRepo = lieuRepo;
    }

    /**
     * @return the CRTS of the same region for a mobile collection, otherwise
     *         the centre itself.  Falls back to the collecting centre when no
     *         regional CRTS is seeded, so a collection is never silently lost.
     */
    public Lieu resolveStockCentre(String centreId) {
        if (centreId == null || centreId.isBlank()) {
            return null;
        }
        Optional<Lieu> centre = lieuRepo.findById(centreId);
        if (centre.isEmpty()) {
            log.warn("Collecting centre {} not found; using it as stock centre", centreId);
            return null;
        }
        Lieu lieu = centre.get();
        if (!TYPE_COLLECTE_MOBILE.equals(lieu.getType())) {
            return lieu;
        }
        return lieuRepo.findByTypeAndRegion(TYPE_CRTS, lieu.getRegion())
                .orElseGet(() -> {
                    log.warn("No CRTS seeded for region {} (mobile centre {}); using the mobile centre",
                            lieu.getRegion(), centreId);
                    return lieu;
                });
    }

    /** Effective centre id sent to CNTS for a collection. */
    public String resolveStockCentreId(String centreId) {
        Lieu resolved = resolveStockCentre(centreId);
        return resolved != null ? resolved.getId() : centreId;
    }
}
