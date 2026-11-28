package com.service_demandes.service;

import com.service_demandes.client.DonStockGrpcClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tn.iit.blood.donstock.v1.NiveauStockMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * S6-11 — Pont entre le stream gRPC {@code SuivreNiveauxStock} et le navigateur.
 *
 * <h3>Architecture</h3>
 * <ol>
 *   <li>Au démarrage ouvre un stream gRPC vers service-don-stock.</li>
 *   <li>Chaque {@link NiveauStockMessage} reçu est stocké dans un cache in-memory
 *       (clé : {@code lieuId|groupeSanguin|composant}) et poussé vers tous les
 *       {@link SseEmitter} connectés.</li>
 *   <li>Quand un client SSE se connecte il reçoit d'abord le snapshot complet du cache,
 *       puis les mises à jour en temps réel.</li>
 *   <li>Un commentaire SSE ({@code : keepalive}) est envoyé toutes les 15 s pour
 *       éviter que les proxies ferment la connexion.</li>
 *   <li>En cas d'erreur ou de fermeture du stream gRPC, une reconnexion est tentée
 *       avec backoff exponentiel (1 s → 2 s → 4 s → … plafonné à 60 s).</li>
 * </ol>
 *
 * <p>Ce bean n'est <em>pas</em> actif en profil {@code mock} où le cache reste vide
 * et le stock est lu via un stub séparé.</p>
 */
@Service
@Profile("!mock")
public class StockBridgeService implements StockBridge {

    private static final Logger log = LoggerFactory.getLogger(StockBridgeService.class);

    /** Délai minimum entre deux tentatives de reconnexion gRPC (ms). */
    private static final long BACKOFF_MIN_MS = 1_000L;
    /** Délai maximum entre deux tentatives de reconnexion gRPC (ms). */
    private static final long BACKOFF_MAX_MS = 60_000L;
    /** Intervalle du keepalive SSE (ms). */
    private static final long KEEPALIVE_INTERVAL_MS = 15_000L;
    /** Timeout par défaut d'un SseEmitter (0 = infini, géré par keepalive + reconnexion côté client). */
    private static final long SSE_TIMEOUT_MS = 0L;

    private final DonStockGrpcClient grpcClient;

    /** Cache : clé = "lieuId|groupeSanguin|composant", valeur = dernier message reçu. */
    private final Map<String, NiveauStockMessage> cache = new ConcurrentHashMap<>();

    /** Clients SSE actuellement connectés. */
    private final List<SseEmitter> emitters = new ArrayList<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "stock-bridge-scheduler");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean streamOuvert = new AtomicBoolean(false);
    private volatile long backoffMs = BACKOFF_MIN_MS;

    public StockBridgeService(DonStockGrpcClient grpcClient) {
        this.grpcClient = grpcClient;
    }

    // -------------------------------------------------------------------------
    // Cycle de vie
    // -------------------------------------------------------------------------

    @PostConstruct
    void demarrer() {
        connecterStream();
        // Keepalive SSE toutes les 15 s
        scheduler.scheduleAtFixedRate(this::envoyerKeepalive,
                KEEPALIVE_INTERVAL_MS, KEEPALIVE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void arreter() {
        streamOuvert.set(false);
        scheduler.shutdownNow();
        synchronized (emitters) {
            emitters.forEach(SseEmitter::complete);
            emitters.clear();
        }
    }

    // -------------------------------------------------------------------------
    // Stream gRPC
    // -------------------------------------------------------------------------

    private void connecterStream() {
        log.info("Connexion au stream gRPC SuivreNiveauxStock (backoff={}ms)", backoffMs);
        streamOuvert.set(true);
        grpcClient.ouvrirStreamNiveauxStock(
                null,              // pas de filtre → snapshot complet de tous les lieux
                this::surMessage,
                this::surErreurStream);
    }

    private void surMessage(NiveauStockMessage msg) {
        String cle = cle(msg);
        cache.put(cle, msg);
        backoffMs = BACKOFF_MIN_MS;   // reset backoff après un message réussi
        diffuserAuxClients(msg);
    }

    private void surErreurStream(Throwable t) {
        streamOuvert.set(false);
        if ("stream-completed".equals(t.getMessage())) {
            log.info("Stream SuivreNiveauxStock terminé normalement — reconnexion dans {}ms", backoffMs);
        } else {
            log.warn("Erreur stream SuivreNiveauxStock : {} — reconnexion dans {}ms", t.getMessage(), backoffMs);
        }
        long delai = backoffMs;
        backoffMs = Math.min(backoffMs * 2, BACKOFF_MAX_MS);
        scheduler.schedule(this::connecterStream, delai, TimeUnit.MILLISECONDS);
    }

    // -------------------------------------------------------------------------
    // SSE — souscription d'un nouveau client
    // -------------------------------------------------------------------------

    /**
     * Crée un {@link SseEmitter} pour un client HTTP.
     * Envoie d'abord le snapshot complet du cache, puis les changements en temps réel.
     *
     * @param lieuId filtre optionnel (null = tout)
     * @return l'émetteur SSE à retourner dans le contrôleur
     */
    public SseEmitter souscrire(String lieuId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        synchronized (emitters) {
            emitters.add(emitter);
        }

        emitter.onCompletion(() -> retirer(emitter));
        emitter.onTimeout(() -> retirer(emitter));
        emitter.onError(e -> retirer(emitter));

        // Snapshot initial du cache
        List<NiveauStockMessage> snapshot = obtenirCache(lieuId);
        try {
            for (NiveauStockMessage msg : snapshot) {
                emitter.send(SseEmitter.event()
                        .name("stock")
                        .data(niveauStockVersMap(msg)));
            }
        } catch (IOException e) {
            retirer(emitter);
        }

        return emitter;
    }

    // -------------------------------------------------------------------------
    // REST — lecture du cache (GET /api/v1/stock/niveaux)
    // -------------------------------------------------------------------------

    /**
     * Retourne le contenu actuel du cache.
     *
     * @param lieuId     filtre optionnel (null = tout)
     * @param groupeStr  filtre optionnel sur le groupe sanguin (null = tout)
     * @return liste des niveaux de stock, triée par lieu puis groupe puis composant
     * @throws ServiceIndisponibleException si le cache est vide et le stream est down
     */
    public List<Map<String, Object>> obtenirNiveaux(String lieuId, String groupeStr) {
        if (cache.isEmpty() && !streamOuvert.get()) {
            throw new ServiceIndisponibleException(
                    "Le service de stock est temporairement indisponible (stream gRPC déconnecté)");
        }

        return cache.values().stream()
                .filter(msg -> lieuId == null || lieuId.isBlank() || lieuId.equals(msg.getLieuId()))
                .filter(msg -> groupeStr == null || groupeStr.isBlank()
                        || groupeStr.equalsIgnoreCase(msg.getGroupeSanguin().name()))
                .sorted(java.util.Comparator
                        .comparing(NiveauStockMessage::getLieuId)
                        .thenComparing(m -> m.getGroupeSanguin().name())
                        .thenComparing(m -> m.getComposant().name()))
                .map(this::niveauStockVersMap)
                .toList();
    }

    /** Indique si le cache contient au moins une entrée (stream fonctionnel). */
    public boolean estConnecte() {
        return streamOuvert.get() || !cache.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers privés
    // -------------------------------------------------------------------------

    private List<NiveauStockMessage> obtenirCache(String lieuId) {
        return cache.values().stream()
                .filter(msg -> lieuId == null || lieuId.isBlank() || lieuId.equals(msg.getLieuId()))
                .toList();
    }

    private void diffuserAuxClients(NiveauStockMessage msg) {
        List<SseEmitter> morts = new ArrayList<>();
        synchronized (emitters) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("stock")
                            .data(niveauStockVersMap(msg)));
                } catch (IOException e) {
                    morts.add(emitter);
                }
            }
            emitters.removeAll(morts);
        }
        if (!morts.isEmpty()) {
            log.debug("{} client(s) SSE déconnecté(s) retiré(s)", morts.size());
        }
    }

    private void envoyerKeepalive() {
        List<SseEmitter> morts = new ArrayList<>();
        synchronized (emitters) {
            for (SseEmitter emitter : emitters) {
                try {
                    // Un commentaire SSE (ligne commençant par ":") maintient la connexion
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (IOException e) {
                    morts.add(emitter);
                }
            }
            emitters.removeAll(morts);
        }
    }

    private void retirer(SseEmitter emitter) {
        synchronized (emitters) {
            emitters.remove(emitter);
        }
    }

    private static String cle(NiveauStockMessage msg) {
        return msg.getLieuId() + "|" + msg.getGroupeSanguin().name() + "|" + msg.getComposant().name();
    }

    /**
     * Convertit un {@link NiveauStockMessage} proto en {@code Map<String, Object>}
     * sérialisable en JSON par Jackson sans dépendance à protobuf-java-util.
     */
    private Map<String, Object> niveauStockVersMap(NiveauStockMessage msg) {
        return Map.of(
                "lieuId",        msg.getLieuId(),
                "lieuNom",       msg.getLieuNom(),
                "groupeSanguin", msg.getGroupeSanguin().name(),
                "composant",     msg.getComposant().name(),
                "disponible",    msg.getDisponible(),
                "reserve",       msg.getReserve(),
                "enTransit",     msg.getEnTransit(),
                "niveau",        msg.getNiveau().name()
        );
    }
}
