package tn.iit.blood.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tn.iit.blood.gateway.filter.RequestIdFilter;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Gestionnaire d'erreurs global du gateway (S2-06).
 *
 * <p>Cette implementation remplace le gestionnaire par defaut de Spring Boot
 * (le bean {@code ErrorWebExceptionHandler} par defaut n'est cree que si aucun
 * autre n'existe). Elle est declaree avec la priorite la plus haute afin de
 * s'executer avant tout autre {@code WebExceptionHandler} et de produire,
 * dans tous les cas, un corps JSON au lieu d'une page HTML.</p>
 *
 * <p>Format de sortie, identique pour toutes les couches :</p>
 * <pre>
 * {
 *   "timestamp": "2026-11-18T16:00:00+01:00",
 *   "status": 503,
 *   "error": "Service Unavailable",
 *   "message": "Le service demande est momentanement indisponible.",
 *   "path": "/api/v1/donneurs",
 *   "requestId": "5b702d6a-c680-4516-bd20-13ca0d9a66c7"
 * }
 * </pre>
 *
 * <p>Le champ {@code requestId} n'est present que si la requete a traverse le
 * filtre S2-05 (routes existantes) ; il reprend l'en-tete {@code X-Request-Id}
 * deja pose sur la reponse.</p>
 *
 * <p>Correspondances exigees :</p>
 * <ul>
 *   <li>connexion refusee (service arrete) -&gt; <b>503</b> ;</li>
 *   <li>delai de reponse depasse -&gt; <b>504</b> ;</li>
 *   <li>route inconnue -&gt; <b>404</b> ;</li>
 *   <li>toute autre erreur -&gt; <b>500</b> (message generique, sans fuite
 *       d'information interne).</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JsonErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonErrorWebExceptionHandler.class);

    private static final String MESSAGE_SERVICE_UNAVAILABLE =
            "Le service demande est momentanement indisponible.";

    private static final String MESSAGE_GATEWAY_TIMEOUT =
            "Le service demande n'a pas repondu dans le delai imparti.";

    private static final String MESSAGE_NOT_FOUND =
            "Aucune ressource ne correspond au chemin demande.";

    private static final String MESSAGE_INTERNAL_ERROR =
            "Erreur interne de la passerelle.";

    /** Profondeur maximale de parcours de la chaine des causes. */
    private static final int MAX_CAUSE_DEPTH = 32;

    private final ObjectMapper objectMapper;

    public JsonErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // Une reponse deja engagee (ex. flux SSE interrompu) ne peut plus etre
        // reecrite : on laisse l'erreur remonter.
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = resolveStatus(ex);
        String message = resolveMessage(status, ex);
        String path = exchange.getRequest().getURI().getRawPath();

        // Identifiant pose par le filtre S2-05, s'il est disponible.
        String requestId = response.getHeaders().getFirst(RequestIdFilter.HEADER_NAME);

        Map<String, Object> body = JsonErrorPayload.create(status, message, path, requestId);
        byte[] payload = JsonErrorPayload.serialize(objectMapper, body, status);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(payload.length);

        log.warn("Reponse d'erreur {} pour {} {} (message={})",
                status.value(),
                exchange.getRequest().getMethod(),
                path,
                message);

        DataBuffer buffer = response.bufferFactory().wrap(payload);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Determine le statut HTTP a renvoyer en parcourant la chaine des causes.
     *
     * <p>La connexion refusee est detectee avant le timeout car certaines
     * exceptions de connexion retournent un delai. Le timeout de connexion
     * (service injoignable) est volontairement classe en 503 ; le 504 est
     * reserve au depassement du delai de reponse ({@code GATEWAY_RESPONSE_TIMEOUT_MS}).</p>
     */
    private HttpStatus resolveStatus(Throwable ex) {
        if (hasCause(ex, ConnectException.class)
                || hasCause(ex, UnknownHostException.class)
                || hasCause(ex, UnresolvedAddressException.class)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (hasCause(ex, TimeoutException.class)) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }

        HttpStatusCode code = null;
        if (ex instanceof ResponseStatusException responseStatusException) {
            code = responseStatusException.getStatusCode();
        }
        else if (ex instanceof ErrorResponse errorResponse) {
            code = errorResponse.getStatusCode();
        }
        if (code != null) {
            HttpStatus resolved = HttpStatus.resolve(code.value());
            if (resolved != null) {
                return resolved;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveMessage(HttpStatus status, Throwable ex) {
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return MESSAGE_SERVICE_UNAVAILABLE;
        }
        if (status == HttpStatus.GATEWAY_TIMEOUT) {
            return MESSAGE_GATEWAY_TIMEOUT;
        }
        if (status == HttpStatus.NOT_FOUND) {
            return MESSAGE_NOT_FOUND;
        }
        if (ex instanceof ResponseStatusException responseStatusException
                && StringUtils.hasText(responseStatusException.getReason())) {
            return responseStatusException.getReason();
        }
        if (status.is5xxServerError()) {
            return MESSAGE_INTERNAL_ERROR;
        }
        return status.getReasonPhrase();
    }

    /** Recherche un type d'exception dans la chaine des causes (avec garde). */
    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSE_DEPTH) {
            if (type.isInstance(current)) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return false;
    }
}
