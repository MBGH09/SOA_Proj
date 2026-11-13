package tn.iit.blood.service_don_stock.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.iit.blood.service_don_stock.event.StockChangeEvent;
import tn.iit.blood.service_don_stock.model.Reservation;
import tn.iit.blood.service_don_stock.model.StockNiveau;
import tn.iit.blood.service_don_stock.repository.ReservationRepository;
import tn.iit.blood.service_don_stock.repository.StockNiveauRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * S4-06 – Delivery simulator.
 * Takes APPROVISIONNEMENT_COMMANDE reservations older than delaiLivraisonSec,
 * moves en_transit → reserve and marks them LIVREE.
 */
@Component
public class DeliverySimulatorJob {

    private static final Logger log = LoggerFactory.getLogger(DeliverySimulatorJob.class);

    @Value("${app.delai-livraison-sec:30}")
    private long delaiLivraisonSec;

    private final ReservationRepository  reservationRepo;
    private final StockNiveauRepository  stockRepo;
    private final ApplicationEventPublisher eventPublisher;

    public DeliverySimulatorJob(ReservationRepository reservationRepo,
                                StockNiveauRepository stockRepo,
                                ApplicationEventPublisher eventPublisher) {
        this.reservationRepo = reservationRepo;
        this.stockRepo        = stockRepo;
        this.eventPublisher   = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${app.delai-livraison-sec:30}000")
    @Transactional
    public void run() {
        Instant cutoff = Instant.now().minusSeconds(delaiLivraisonSec);
        List<Reservation> toDeliver = reservationRepo
            .findByStatutAndCreatedAtBefore("APPROVISIONNEMENT_COMMANDE", cutoff);

        if (toDeliver.isEmpty()) return;
        log.info("Delivery simulator: processing {} orders", toDeliver.size());

        List<StockNiveau> changed = new ArrayList<>();
        for (Reservation r : toDeliver) {
            stockRepo.findByKeyForUpdate(r.getLieuId(), r.getGroupeSanguin(), r.getComposant())
                .ifPresent(stock -> {
                    // Move from en_transit to reserve (pouches arrive, allocated to this request)
                    int qty = Math.min(r.getQuantite(), stock.getEnTransit());
                    stock.setEnTransit(stock.getEnTransit() - qty);
                    stock.setReserve(stock.getReserve() + qty);
                    stockRepo.save(stock);
                    changed.add(stock);
                });
            r.setStatut("LIVREE");
            reservationRepo.save(r);
            log.info("Reservation {} delivered", r.getDemandeId());
        }

        if (!changed.isEmpty()) {
            eventPublisher.publishEvent(new StockChangeEvent(this, changed));
        }
    }
}
