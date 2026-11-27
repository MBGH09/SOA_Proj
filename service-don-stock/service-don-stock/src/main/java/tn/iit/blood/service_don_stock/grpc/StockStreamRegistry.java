package tn.iit.blood.service_don_stock.grpc;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.iit.blood.donstock.v1.NiveauStockMessage;
import tn.iit.blood.donstock.v1.SuivreNiveauxStockRequest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * S4-10 – Registry of active streaming subscribers.
 * Thread-safe: multiple gRPC threads may add/remove subscribers concurrently.
 */
@Component
public class StockStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(StockStreamRegistry.class);

    private final AtomicLong counter = new AtomicLong();

    record Subscriber(
        String id,
        StreamObserver<NiveauStockMessage> observer,
        String lieuIdFilter,       // null = no filter
        String groupeFilter        // null = no filter (name of proto enum)
    ) {}

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();

    public String register(SuivreNiveauxStockRequest request,
                           StreamObserver<NiveauStockMessage> observer) {
        String id = "sub-" + counter.incrementAndGet();
        String lieuFilter  = request.getLieuId().isBlank()          ? null : request.getLieuId();
        String groupFilter = request.getGroupeSanguin().getNumber() == 0 ? null
                           : request.getGroupeSanguin().name();

        Subscriber sub = new Subscriber(id, observer, lieuFilter, groupFilter);
        subscribers.put(id, sub);

        // Auto-remove when the client cancels
        if (observer instanceof ServerCallStreamObserver<NiveauStockMessage> scso) {
            scso.setOnCancelHandler(() -> {
                subscribers.remove(id);
                log.info("Stream subscriber {} cancelled and removed", id);
            });
        }

        log.info("Stream subscriber {} registered (lieu={}, groupe={})", id, lieuFilter, groupFilter);
        return id;
    }

    public void unregister(String id) {
        subscribers.remove(id);
    }

    /**
     * Push a message to all matching subscribers.
     */
    public void publish(NiveauStockMessage message) {
        for (Subscriber sub : subscribers.values()) {
            if (matches(sub, message)) {
                try {
                    sub.observer().onNext(message);
                } catch (Exception e) {
                    log.warn("Failed to push to subscriber {}, removing: {}", sub.id(), e.getMessage());
                    subscribers.remove(sub.id());
                }
            }
        }
    }

    private boolean matches(Subscriber sub, NiveauStockMessage msg) {
        if (sub.lieuIdFilter() != null && !sub.lieuIdFilter().equals(msg.getLieuId())) return false;
        if (sub.groupeFilter()  != null && !sub.groupeFilter().equals(msg.getGroupeSanguin().name())) return false;
        return true;
    }

    public boolean isEmpty() {
        return subscribers.isEmpty();
    }
}
