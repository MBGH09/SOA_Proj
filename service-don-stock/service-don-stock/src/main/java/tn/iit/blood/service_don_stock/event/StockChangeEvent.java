package tn.iit.blood.service_don_stock.event;

import org.springframework.context.ApplicationEvent;
import tn.iit.blood.service_don_stock.model.StockNiveau;

import java.util.List;

/**
 * Published after every database commit that changes stock levels.
 * The streaming gRPC service (S4-10) listens to this event.
 */
public class StockChangeEvent extends ApplicationEvent {

    private final List<StockNiveau> changedRows;

    public StockChangeEvent(Object source, List<StockNiveau> changedRows) {
        super(source);
        this.changedRows = changedRows;
    }

    public List<StockNiveau> getChangedRows() {
        return changedRows;
    }
}
