package tn.iit.blood.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tn.iit.blood.gateway.filter.RequestIdFilter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

/**
 * Point d'entree de connexion (S2-07).
 *
 * <p>Compare les identifiants aux quatre utilisateurs de demonstration
 * configures par variables d'environnement, puis renvoie un JWT signe en
 * HS256 valable une heure. Aucun mot de passe n'est stocke dans le depot.</p>
 *
 * <p>Les erreurs passent par le gestionnaire JSON de S2-06 : 401 pour des
 * identifiants invalides, 400 pour une requete incomplete.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    static final String MESSAGE_MISSING_CREDENTIALS =
            "Nom d'utilisateur et mot de passe obligatoires.";

    static final String MESSAGE_BAD_CREDENTIALS =
            "Identifiants invalides.";

    private final DemoUserService demoUsers;

    private final JwtTokenService tokens;

    public LoginController(DemoUserService demoUsers, JwtTokenService tokens) {
        this.demoUsers = demoUsers;
        this.tokens = tokens;
    }

    @PostMapping("/login")
    public Mono<LoginResponse> login(@RequestBody(required = false) LoginRequest request,
                                     ServerWebExchange exchange) {
        String requestId = ensureRequestId(exchange);

        if (request == null
                || !StringUtils.hasText(request.username())
                || !StringUtils.hasText(request.password())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, MESSAGE_MISSING_CREDENTIALS));
        }

        String username = request.username().trim();
        Optional<DemoUser> user = demoUsers.findByUsername(username);

        if (user.isEmpty() || !passwordMatches(request.password(), user.get().password())) {
            log.warn("Echec de connexion pour {} (requestId={})", username, requestId);
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, MESSAGE_BAD_CREDENTIALS));
        }

        String token = tokens.issueToken(user.get());
        log.info("Connexion reussie pour {} (role={}, requestId={})",
                user.get().username(), user.get().role(), requestId);

        return Mono.just(new LoginResponse(token));
    }

    /**
     * Compare les mots de passe sans court-circuit evident. Les utilisateurs de
     * demonstration etant configures localement, ce mode reste volontairement
     * simple ; un stockage hache relevera d'un vrai annuaire.
     */
    private static boolean passwordMatches(String supplied, String expected) {
        return MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Pose un identifiant de requete pour les chemins non routes par le
     * gateway, afin que le gestionnaire d'erreurs S2-06 puisse le reprendre.
     */
    private static String ensureRequestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders()
                .getFirst(RequestIdFilter.HEADER_NAME);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        exchange.getResponse().getHeaders().set(RequestIdFilter.HEADER_NAME, requestId);
        return requestId;
    }
}
