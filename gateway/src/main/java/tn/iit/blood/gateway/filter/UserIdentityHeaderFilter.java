package tn.iit.blood.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tn.iit.blood.gateway.security.JwtTokenService;

/**
 * En-tetes d'identite transmis aux microservices (S2-08).
 *
 * <p>Le gateway est le seul composant autorise a designer l'utilisateur :</p>
 * <ul>
 *   <li>{@code X-User-Id} et {@code X-User-Role} fournis par le client sont
 *       <strong>toujours supprimes</strong>, meme sur une route publique ;</li>
 *   <li>apres verification du jeton, ils sont reecrits a partir des claims
 *       {@code sub} et {@code role} du JWT verifie : un client ne peut donc
 *       pas s'attribuer un autre identifiant ou un autre role.</li>
 * </ul>
 *
 * <p><strong>Limite connue (gap 7 differe) :</strong> {@code X-User-Id} porte
 * le nom de connexion ({@code sub}), pas un identifiant de donneur ou
 * d'hopital. Les regles de propriete (« un donneur ne lit que ses propres
 * donnees ») ne peuvent donc pas etre appliquees par le gateway ; elles sont
 * deleguees au service Donneurs, qui n'existe pas encore localement.</p>
 */
@Component
public class UserIdentityHeaderFilter implements GlobalFilter, Ordered {

    /** Identite de l'utilisateur authentifie (claim {@code sub}). */
    public static final String USER_ID_HEADER = "X-User-Id";

    /** Role officiel de l'utilisateur authentifie (claim {@code role}). */
    public static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. Ne jamais faire confiance a un en-tete fourni par le client.
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                })
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitized).build();

        // 2. Ajouter l'identite uniquement si le jeton a ete verifie.
        return currentAuthentication(exchange)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(token -> withIdentityHeaders(sanitizedExchange, token))
                .defaultIfEmpty(sanitizedExchange)
                .flatMap(chain::filter);
    }

    /**
     * Recupere l'authentification posee par Spring Security. Le contexte
     * Reactor est la source normale ; le principal de l'echange sert de
     * repli lorsque le contexte n'a pas ete propage.
     */
    private static Mono<Authentication> currentAuthentication(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .switchIfEmpty(Mono.defer(
                        () -> exchange.getPrincipal().ofType(Authentication.class)));
    }

    private static ServerWebExchange withIdentityHeaders(ServerWebExchange exchange,
                                                         JwtAuthenticationToken token) {
        String userId = token.getToken().getSubject();
        String role = token.getToken().getClaimAsString(JwtTokenService.ROLE_CLAIM);
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(role)) {
            return exchange;
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(USER_ID_HEADER, userId);
                    headers.set(USER_ROLE_HEADER, role);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
