package tn.iit.blood.service_don_stock.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import tn.iit.blood.donstock.v1.NiveauStockMessage;
import tn.iit.blood.service_don_stock.event.StockChangeEvent;
import tn.iit.blood.service_don_stock.model.Lieu;
import tn.iit.blood.service_don_stock.model.StockNiveau;
import tn.iit.blood.service_don_stock.repository.LieuRepository;

/**
 * S4-10 – After every transaction that changes stock,
 * publish the updated rows to all stream subscribers.
 */
@Component
public class StockChangeEventListener {

    private static final Logger log = LoggerFactory.getLogger(StockChangeEventListener.class);

    private final StockStreamRegistry registry;
    private final LieuRepository lieuRepo;

    public StockChangeEventListener(StockStreamRegistry registry, LieuRepository lieuRepo) {
        this.registry = registry;
        this.lieuRepo  = lieuRepo;
    }

    /**
     * Fires AFTER COMMIT so the gRPC stream only delivers committed data.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChange(StockChangeEvent event) {
        if (registry.isEmpty()) return;

        for (StockNiveau s : event.getChangedRows()) {
            Lieu lieu = lieuRepo.findById(s.getLieuId()).orElse(null);
            NiveauStockMessage msg = StockNiveauMapper.toProto(s, lieu);
            registry.publish(msg);
        }
        log.debug("Published {} stock change(s) to stream subscribers", event.getChangedRows().size());
    }
}
