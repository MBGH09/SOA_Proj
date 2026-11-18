package tn.iit.blood.gateway.security;

import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import tn.iit.blood.gateway.filter.RequestIdFilter;

import java.util.UUID;

/**
 * Resolution de l'identifiant de requete pour les reponses de securite
 * (S2-07 et S2-08).
 *
 * <p>Les filtres de securite s'executent avant le routage du gateway : le
 * filtre {@code X-Request-Id} de S2-05 n'a donc pas encore pose l'identifiant.
 * Il est repris de l'en-tete entrant s'il existe, sinon genere ici et renvoye
 * au client, afin que les erreurs 401 et 403 restent correlables aux logs.</p>
 */
final class RequestIdSupport {

    private RequestIdSupport() {
    }

    static String resolve(ServerWebExchange exchange) {
        String requestId = exchange.getResponse().getHeaders()
                .getFirst(RequestIdFilter.HEADER_NAME);
        if (!StringUtils.hasText(requestId)) {
            requestId = exchange.getRequest().getHeaders()
                    .getFirst(RequestIdFilter.HEADER_NAME);
        }
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        return requestId;
    }
}
