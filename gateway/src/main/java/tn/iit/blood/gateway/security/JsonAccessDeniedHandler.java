package tn.iit.blood.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tn.iit.blood.gateway.error.JsonErrorPayload;
import tn.iit.blood.gateway.filter.RequestIdFilter;

import java.util.Map;

/**
 * Reponse JSON pour un utilisateur authentifie mais non autorise (S2-08).
 *
 * <p>Quand un jeton valide ne porte pas le role exige par la route, Spring
 * Security leve une {@link AccessDeniedException} avant le routage. Sans ce
 * composant, la reponse serait vide (ou HTML). Il ecrit exactement le meme
 * contrat que le gestionnaire S2-06 et que le 401 de S2-07 :
 * {@code timestamp}, {@code status}, {@code error}, {@code message},
 * {@code path} et {@code requestId}.</p>
 *
 * <p>Un 403 ne porte pas {@code WWW-Authenticate} : le client est authentifie,
 * seul son role ne permet pas l'operation.</p>
 */
@Component
public class JsonAccessDeniedHandler implements ServerAccessDeniedHandler {

    private static final Logger log =
            LoggerFactory.getLogger(JsonAccessDeniedHandler.class);

    static final String MESSAGE_FORBIDDEN =
            "Acces refuse : le role de l'utilisateur ne permet pas cette operation.";

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(denied);
        }

        String path = exchange.getRequest().getURI().getRawPath();
        String requestId = RequestIdSupport.resolve(exchange);

        Map<String, Object> body = JsonErrorPayload.create(
                HttpStatus.FORBIDDEN, MESSAGE_FORBIDDEN, path, requestId);
        byte[] payload = JsonErrorPayload.serialize(objectMapper, body, HttpStatus.FORBIDDEN);

        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(payload.length);
        response.getHeaders().set(RequestIdFilter.HEADER_NAME, requestId);

        log.warn("Reponse d'erreur 403 pour {} {} (requestId={})",
                exchange.getRequest().getMethod(), path, requestId);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
    }
}
