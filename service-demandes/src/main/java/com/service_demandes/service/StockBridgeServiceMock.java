package com.service_demandes.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Version mock du pont stock, active en profil {@code mock}.
 * Envoie des niveaux fictifs qui changent toutes les 5 secondes pour
 * permettre de développer le frontend sans service-don-stock.
 */
@Service
@Profile("mock")
public class StockBridgeServiceMock implements StockBridge {

    private static final Logger log = LoggerFactory.getLogger(StockBridgeServiceMock.class);

    private final List<SseEmitter> emitters = new ArrayList<>();
    private final AtomicInteger tick = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "stock-mock-scheduler"));

    @PostConstruct
    void demarrer() {
        scheduler.scheduleAtFixedRate(this::envoyerMaj, 5, 5, TimeUnit.SECONDS);
    }

    public SseEmitter souscrire(String lieuId) {
        SseEmitter emitter = new SseEmitter(0L);
        synchronized (emitters) {
            emitters.add(emitter);
        }
        emitter.onCompletion(() -> retirer(emitter));
        emitter.onTimeout(() -> retirer(emitter));
        emitter.onError(e -> retirer(emitter));

        // Snapshot initial
        try {
            emitter.send(SseEmitter.event().name("stock").data(niveauFactice("CHU-1", "O_NEG", "CGR", 2, "BAS")));
            emitter.send(SseEmitter.event().name("stock").data(niveauFactice("CHU-1", "A_POS", "CGR", 12, "MOYEN")));
        } catch (IOException e) {
            retirer(emitter);
        }
        return emitter;
    }

    public List<Map<String, Object>> obtenirNiveaux(String lieuId, String groupe) {
        return List.of(
                niveauFactice("CHU-1", "O_NEG",  "CGR",        2, "BAS"),
                niveauFactice("CHU-1", "A_POS",  "CGR",       12, "MOYEN"),
                niveauFactice("CHU-1", "B_POS",  "PLASMA",    20, "HAUT"),
                niveauFactice("CRTS-1","O_POS",  "PLAQUETTES", 5, "MOYEN")
        );
    }

    public boolean estConnecte() { return true; }

    private void envoyerMaj() {
        int t = tick.incrementAndGet();
        Map<String, Object> msg = niveauFactice("CHU-1", "O_NEG", "CGR", t % 10, t % 10 <= 2 ? "BAS" : "MOYEN");
        List<SseEmitter> morts = new ArrayList<>();
        synchronized (emitters) {
            for (SseEmitter e : emitters) {
                try {
                    e.send(SseEmitter.event().name("stock").data(msg));
                } catch (IOException ex) {
                    morts.add(e);
                }
            }
            emitters.removeAll(morts);
        }
    }

    private void retirer(SseEmitter e) {
        synchronized (emitters) { emitters.remove(e); }
    }

    private Map<String, Object> niveauFactice(String lieu, String groupe, String composant,
                                               int disponible, String niveau) {
        return Map.of(
                "lieuId",        lieu,
                "lieuNom",       "Mock " + lieu,
                "groupeSanguin", groupe,
                "composant",     composant,
                "disponible",    disponible,
                "reserve",       0,
                "enTransit",     0,
                "niveau",        niveau
        );
    }
}
