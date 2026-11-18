package tn.iit.blood.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Filtre global d'identification des requetes (S2-05).
 *
 * <p>Si l'en-tete {@code X-Request-Id} est absent (ou vide), un UUID est genere.
 * L'identifiant est ensuite :</p>
 * <ul>
 *   <li>ajoute a la requete transmise au microservice (meme valeur) ;</li>
 *   <li>ajoute a la response renvoyee au client ;</li>
 *   <li>place dans le MDC et journalise sur la sortie standard, avec un motif
 *       de logs qui affiche {@code [requestId]} sur chaque ligne emise pendant
 *       le traitement de la requete.</li>
 * </ul>
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    /** Nom de l'en-tete HTTP utilise de bout en bout. */
    public static final String HEADER_NAME = "X-Request-Id";

    /** Cle MDC utilisee par le motif de logs (logging.pattern.console). */
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        String resolvedRequestId = requestId;

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER_NAME, resolvedRequestId))
                .build();

        exchange.getResponse().getHeaders().set(HEADER_NAME, resolvedRequestId);

        MDC.put(MDC_KEY, resolvedRequestId);
        log.info("Requete recue: {} {} (requestId={})",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getRawPath(),
                resolvedRequestId);

        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(signal -> {
                    // Le doFinally peut s'executer sur un autre thread reactif :
                    // on retablit le MDC avant de journaliser la fin de requete.
                    MDC.put(MDC_KEY, resolvedRequestId);
                    try {
                        log.info("Requete terminee: {} {} statut={} (requestId={})",
                                exchange.getRequest().getMethod(),
                                exchange.getRequest().getURI().getRawPath(),
                                exchange.getResponse().getStatusCode(),
                                resolvedRequestId);
                    }
                    finally {
                        MDC.remove(MDC_KEY);
                    }
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
