package tn.iit.blood.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tn.iit.blood.gateway.error.JsonErrorPayload;
import tn.iit.blood.gateway.filter.RequestIdFilter;

import java.util.Map;

/**
 * Reponse JSON pour une requete non authentifiee (S2-07).
 *
 * <p>Spring Security intercepte les requetes sans jeton avant le routage du
 * gateway. Sans ce composant, la reponse serait vide (ou HTML). Il ecrit le
 * meme contrat que le gestionnaire S2-06 : {@code timestamp}, {@code status},
 * {@code error}, {@code message}, {@code path} et, si possible,
 * {@code requestId}.</p>
 *
 * <p>Le filtre S2-05 etant un filtre de routage, il ne s'execute pas pour les
 * chemins non routes (connexion, health, refus d'acces). L'identifiant est donc
 * repris de l'en-tete entrant ou genere ici, puis renvoye au client.</p>
 */
@Component
public class JsonAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private static final Logger log =
            LoggerFactory.getLogger(JsonAuthenticationEntryPoint.class);

    static final String MESSAGE_UNAUTHORIZED =
            "Un jeton d'acces valide est requis.";

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        // Spring Security construit ce Mono de secours avant de savoir s'il
        // servira (repli du 403). Tout l'effet de bord est donc differe dans un
        // Mono.defer : une reponse 403 n'est jamais polluee par un statut 401,
        // un en-tete WWW-Authenticate ou une ligne de journal trompeuse.
        return Mono.defer(() -> {
            ServerHttpResponse response = exchange.getResponse();
            if (response.isCommitted()) {
                return Mono.error(ex);
            }

            String path = exchange.getRequest().getURI().getRawPath();
            String requestId = RequestIdSupport.resolve(exchange);

            Map<String, Object> body = JsonErrorPayload.create(
                    HttpStatus.UNAUTHORIZED, MESSAGE_UNAUTHORIZED, path, requestId);
            byte[] payload = JsonErrorPayload.serialize(objectMapper, body, HttpStatus.UNAUTHORIZED);

            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getHeaders().setContentLength(payload.length);
            response.getHeaders().set(RequestIdFilter.HEADER_NAME, requestId);
            response.getHeaders().set("WWW-Authenticate", "Bearer");

            log.warn("Reponse d'erreur 401 pour {} {} (requestId={})",
                    exchange.getRequest().getMethod(), path, requestId);

            return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
        });
    }
}
