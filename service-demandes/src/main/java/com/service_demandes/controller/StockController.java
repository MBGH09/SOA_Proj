package com.service_demandes.controller;

import com.service_demandes.service.StockBridge;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * S6-11 — Pont stock REST + SSE.
 *
 * <table border="1">
 *   <tr><th>Méthode</th><th>Chemin</th><th>Codes</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/v1/stock/niveaux</td>
 *       <td>200, 503</td>
 *       <td>Niveaux courants (lecture du cache in-memory, pont gRPC)</td></tr>
 *   <tr><td>GET</td><td>/api/v1/stock/niveaux/flux</td>
 *       <td>200</td>
 *       <td>Server-sent events : snapshot puis mises à jour en temps réel</td></tr>
 * </table>
 *
 * <h3>Paramètres de filtre</h3>
 * <ul>
 *   <li>{@code lieuId} — filtre optionnel par identifiant de lieu</li>
 *   <li>{@code groupe} — filtre optionnel par groupe sanguin (ex: {@code O_NEG})</li>
 * </ul>
 *
 * <h3>Format d'un événement SSE</h3>
 * <pre>
 * event: stock
 * data: {"lieuId":"CHU-1","lieuNom":"CHU Habib Bourguiba Sfax","groupeSanguin":"O_NEG",
 *         "composant":"CGR","disponible":2,"reserve":0,"enTransit":0,"niveau":"BAS"}
 * </pre>
 *
 * <p>Un commentaire {@code : keepalive} est envoyé toutes les 15 s par le service
 * pour maintenir la connexion à travers les proxies.</p>
 *
 * <h3>Comportement en cas de panne</h3>
 * <ul>
 *   <li>Si le stream gRPC est coupé, {@code GET /niveaux} retourne 503 quand le cache est vide.</li>
 *   <li>Le stream SSE reste ouvert ; dès que gRPC se reconnecte les nouveaux événements arrivent.</li>
 *   <li>Le frontend doit se reconnecter en SSE sur erreur et basculer sur polling
 *       {@code GET /niveaux} toutes les 5 s en attendant.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/stock/niveaux")
public class StockController {

    private final StockBridge bridge;

    public StockController(StockBridge bridge) {
        this.bridge = bridge;
    }

    // -------------------------------------------------------------------------
    // REST : lecture du cache
    // -------------------------------------------------------------------------

    /**
     * Retourne les niveaux de stock actuels depuis le cache in-memory.
     * Répond 503 si le cache est vide et le stream gRPC est down.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> niveaux(
            @RequestParam(required = false) String lieuId,
            @RequestParam(required = false) String groupe) {
        return bridge.obtenirNiveaux(lieuId, groupe);
    }

    // -------------------------------------------------------------------------
    // SSE : flux temps réel
    // -------------------------------------------------------------------------

    /**
     * Ouvre un flux Server-Sent Events vers le navigateur.
     *
     * <p>La route {@code /flux} doit avoir un {@code response-timeout} désactivé (négatif)
     * dans la configuration du Gateway (S2-04), car la réponse ne se termine jamais.</p>
     *
     * <p>Le client doit utiliser l'API {@code EventSource} :
     * <pre>
     * const es = new EventSource('/api/v1/stock/niveaux/flux');
     * es.addEventListener('stock', e => {
     *   const niveau = JSON.parse(e.data);
     *   // mettre à jour la jauge correspondante
     * });
     * es.onerror = () => { es.close(); /* basculer sur polling *\/ };
     * </pre>
     * </p>
     */
    @GetMapping(value = "/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter flux(@RequestParam(required = false) String lieuId) {
        return bridge.souscrire(lieuId);
    }
}
