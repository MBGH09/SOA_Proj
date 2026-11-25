package tn.iit.blood.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tn.iit.blood.gateway.error.JsonErrorPayload;
import tn.iit.blood.gateway.filter.RequestIdFilter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Limitation de debit par adresse IP a l'entree du gateway (S2-09).
 *
 * <p>Le filtre est insere dans la chaine Spring Security apres le traitement
 * CORS et avant l'authentification. Il voit donc toutes les requetes :
 * routes publiques, routes protegees, requetes anonymes et
 * {@code /api/v1/auth/login}. Les reponses 429 conservent les en-tetes CORS
 * deja poses par Spring Security.</p>
 *
 * <p>Sont volontairement ignores :</p>
 * <ul>
 *   <li>{@code /actuator/health} et ses sous-routes (supervision) ;</li>
 *   <li>exactement {@code /api/v1/stock/niveaux/flux}, le flux SSE de S2-04 :
 *       il n'est ni compte ni transforme, afin de ne pas interrompre le
 *       streaming ;</li>
 *   <li>les preflights {@code OPTIONS}, pour ne pas casser le CORS.</li>
 * </ul>
 *
 * <p>La cle est l'adresse distante de la connexion. {@code X-Forwarded-For}
 * n'est pas utilise : cet en-tete est fourni par le client et falsifiable tant
 * qu'aucun proxy de confiance n'est configure.</p>
 *
 * <p>Ce filtre n'est pas un {@code @Component} : il est instancie une seule
 * fois dans {@link SecurityConfiguration}. Un bean WebFilter serait en plus
 * auto-enregistre par WebFlux, ce qui appliquerait la limite deux fois.</p>
 */
public class RateLimitWebFilter implements WebFilter {

    private static final Logger log =
            LoggerFactory.getLogger(RateLimitWebFilter.class);

    static final String MESSAGE_TOO_MANY_REQUESTS =
            "Trop de requetes : la limite de la passerelle est atteinte.";

    private static final String HEALTH_PATH = "/actuator/health";

    private static final String STREAM_PATH = "/api/v1/stock/niveaux/flux";

    private static final String RETRY_AFTER_HEADER = "Retry-After";

    /** Cle utilisee lorsque l'adresse distante n'est pas disponible. */
    static final String UNKNOWN_CLIENT = "unknown";

    private final RateLimitCounter counter;

    private final ObjectMapper objectMapper;

    public RateLimitWebFilter(RateLimitCounter counter, ObjectMapper objectMapper) {
        this.counter = counter;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isExcluded(exchange.getRequest())) {
            return chain.filter(exchange);
        }

        String clientIp = clientIp(exchange);
        RateLimitCounter.Decision decision = counter.tryAcquire(clientIp);
        if (decision.allowed()) {
            return chain.filter(exchange);
        }

        return reject(exchange, clientIp, decision.retryAfterSeconds());
    }

    /**
     * Indique si la requete echappe a la limitation : preflight CORS, sante et
     * flux temps reel.
     */
    private boolean isExcluded(ServerHttpRequest request) {
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return true;
        }
        String path = request.getURI().getRawPath();
        if (path == null) {
            return false;
        }
        return path.equals(HEALTH_PATH)
                || path.startsWith(HEALTH_PATH + "/")
                || path.equals(STREAM_PATH);
    }

    /**
     * Ecrit la reponse 429 au format JSON commun, avec {@code Retry-After} et
     * {@code X-Request-Id}. Aucune ecriture n'est faite sur le corps du flux
     * amont : la requete refusee n'atteint jamais le routage.
     */
    private Mono<Void> reject(ServerWebExchange exchange,
                              String clientIp,
                              long retryAfterSeconds) {

        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }

        String path = exchange.getRequest().getURI().getRawPath();
        String requestId = RequestIdSupport.resolve(exchange);

        Map<String, Object> body = JsonErrorPayload.create(
                HttpStatus.TOO_MANY_REQUESTS, MESSAGE_TOO_MANY_REQUESTS, path, requestId);
        byte[] payload = JsonErrorPayload.serialize(
                objectMapper, body, HttpStatus.TOO_MANY_REQUESTS);

        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(payload.length);
        response.getHeaders().set(RequestIdFilter.HEADER_NAME, requestId);
        response.getHeaders().set(RETRY_AFTER_HEADER, Long.toString(retryAfterSeconds));

        log.warn("Reponse d'erreur 429 pour {} {} depuis {} (requestId={}, retryAfter={}s)",
                exchange.getRequest().getMethod(), path, clientIp, requestId,
                retryAfterSeconds);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
    }

    /**
     * Adresse distante de la connexion, sans tenir compte des en-tetes
     * {@code Forwarded} ou {@code X-Forwarded-For}.
     */
    private static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null) {
            return UNKNOWN_CLIENT;
        }
        InetAddress address = remoteAddress.getAddress();
        return address != null ? address.getHostAddress() : remoteAddress.getHostString();
    }
}
