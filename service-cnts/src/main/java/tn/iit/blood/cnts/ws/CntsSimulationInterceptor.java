package tn.iit.blood.cnts.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.SoapMessage;

import java.util.Locale;

/**
 * S5-09 — Simulation CNTS.
 *
 * Simule deux comportements d'un système national legacy :
 *  1. Latence artificielle  → CNTS_LATENCY_MS  (défaut 0 ms)
 *  2. Taux de panne aléatoire → CNTS_FAIL_RATE  (défaut 0.0 = jamais)
 *
 * Ces deux valeurs proviennent UNIQUEMENT des variables d'environnement
 * (jamais hardcodées — règle P0 de AGENTS.md).
 *
 * L'intercepteur est enregistré après le PayloadValidatingInterceptor
 * dans WebServiceConfig.addInterceptors() : la validation XSD passe
 * d'abord, la simulation s'applique ensuite sur les requêtes valides.
 */
@Slf4j
@Component
public class CntsSimulationInterceptor implements EndpointInterceptor {

    /** Latence simulée en ms. 0 = pas de latence. */
    @Value("${cnts.latency-ms:0}")
    private long latencyMs;

    /**
     * Taux de panne simulé [0.0 – 1.0].
     * Exemple : 0.1 → 10 % des appels échouent avec une Server-Fault.
     * 0.0 = jamais de panne.
     */
    @Value("${cnts.fail-rate:0.0}")
    private double failRate;

    /**
     * Point d'entrée : appelé avant que la requête n'atteigne l'endpoint.
     *
     * @return true  → continuer vers l'endpoint
     *         false → court-circuit, la réponse (Fault) est déjà écrite
     */
    @Override
    public boolean handleRequest(MessageContext messageContext, Object endpoint) throws Exception {

        // 1. Latence simulée
        if (latencyMs > 0) {
            log.debug("CNTS simulation: injection latence {}ms", latencyMs);
            Thread.sleep(latencyMs);
        }

        // 2. Panne aléatoire
        if (failRate > 0.0 && Math.random() < failRate) {
            log.warn("CNTS simulation: injection panne (fail-rate={})", failRate);
            SoapMessage response = (SoapMessage) messageContext.getResponse();
            response.getSoapBody().addServerOrReceiverFault(
                    "Service CNTS temporairement indisponible (simulation)", Locale.FRENCH);
            return false; // court-circuit : ne pas appeler l'endpoint
        }

        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext, Object endpoint) {
        return true; // rien à faire sur la réponse
    }

    @Override
    public boolean handleFault(MessageContext messageContext, Object endpoint) {
        return true; // rien à faire sur les faults
    }

    @Override
    public void afterCompletion(MessageContext messageContext, Object endpoint, Exception ex) {
        // rien à faire après completion
    }
}
