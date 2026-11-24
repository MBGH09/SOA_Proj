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
import reactor.util.context.ContextView;

import java.util.UUID;

/**
 * Filtre global d'identification des requetes (S2-05).
 *
 * <p>Si l'en-tete {@code X-Request-Id} est absent (ou vide), un UUID est genere.
 * L'identifiant est ensuite :</p>
 * <ul>
 *   <li>ajoute a la requete transmise au microservice (meme valeur) ;</li>
 *   <li>ajoute a la response renvoyee au client ;</li>
 *   <li>place dans le <em>Reactor Context</em> : c'est la source de verite pour
 *       toutes les operations reactives de la requete, quel que soit le thread
 *       qui les execute ;</li>
 *   <li>journalise avec un MDC <em>portee limitee</em> : le MDC est pose
 *       uniquement autour de l'ecriture du log, puis la valeur precedente est
 *       restauree. Aucun identifiant ne reste attache a un thread du pool
 *       Netty apres la requete.</li>
 * </ul>
 *
 * <p>Les composants reactifs peuvent recuperer l'identifiant courant avec
 * {@link #currentRequestId()} (ou via {@link #CONTEXT_KEY} et
 * {@code Mono.deferContextual}) au lieu de lire le MDC.</p>
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    /** Nom de l'en-tete HTTP utilise de bout en bout. */
    public static final String HEADER_NAME = "X-Request-Id";

    /** Cle MDC utilisee par le motif de logs (logging.pattern.console). */
    public static final String MDC_KEY = "requestId";

    /** Cle du Reactor Context porteuse de l'identifiant de requete. */
    public static final String CONTEXT_KEY = RequestIdFilter.class.getName() + ".requestId";

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

        String method = String.valueOf(exchange.getRequest().getMethod());
        String path = exchange.getRequest().getURI().getRawPath();

        // Le contexte est ecrit "en aval" de deferContextual : il est donc
        // visible par toute la chaine placee en amont, y compris les filtres
        // suivants et les traitements qui changent de thread.
        return Mono.deferContextual(context -> {
            String id = context.getOrDefault(CONTEXT_KEY, resolvedRequestId);

            logWithRequestId(id, () -> log.info("Requete recue: {} {} (requestId={})",
                    method, path, id));

            return chain.filter(exchange.mutate().request(request).build())
                    .doFinally(signal -> logWithRequestId(id, () ->
                            log.info("Requete terminee: {} {} statut={} (requestId={})",
                                    method, path, exchange.getResponse().getStatusCode(), id)));
        }).contextWrite(context -> context.put(CONTEXT_KEY, resolvedRequestId));
    }

    /**
     * Identifiant de la requete en cours, lu depuis le Reactor Context.
     *
     * <p>Cette methode remplace une lecture du MDC des que le traitement est
     * reactif : le contexte suit la souscription, pas le thread.</p>
     *
     * @return l'identifiant, ou un {@code Mono} vide hors du traitement d'une
     *         requete ayant traverse ce filtre
     */
    public static Mono<String> currentRequestId() {
        return Mono.deferContextual(context -> {
            ContextView view = context;
            return view.hasKey(CONTEXT_KEY)
                    ? Mono.just(view.<String>get(CONTEXT_KEY))
                    : Mono.empty();
        });
    }

    /**
     * Ecrit un log avec le MDC aligne sur l'identifiant de la requete, puis
     * restaure exactement la valeur precedente. Cela evite qu'un thread du pool
     * Netty garde (ou transmette a une autre requete) un identifiant erronne.
     */
    private static void logWithRequestId(String requestId, Runnable action) {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, requestId);
        try {
            action.run();
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
