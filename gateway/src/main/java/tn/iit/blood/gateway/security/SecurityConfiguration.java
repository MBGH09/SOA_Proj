package tn.iit.blood.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration de securite reactive du gateway (S2-07 et S2-08).
 *
 * <p>Le gateway verifie lui-meme les jetons HS256 avec {@code JWT_SECRET} :
 * aucun appel a un autre service n'est necessaire. Les chemins publics sont
 * ceux de S2-08 (lecture des centres, appels et verification de certificat,
 * inscription d'un donneur), plus {@code /actuator/health} et
 * {@code /api/v1/auth/**}.</p>
 *
 * <p>S2-08 ajoute les regles de role de l'enonce : un jeton valide mais dont
 * le role n'est pas autorise recoit un <strong>403 JSON</strong>. Les regles
 * portent uniquement sur le role ; la propriete des donnees (« un donneur ne
 * lit que ses propres donnees ») est deleguee au service Donneurs via les
 * en-tetes {@code X-User-Id}/{@code X-User-Role}, car le gap 7 est differe.</p>
 *
 * <p>{@code gateway.security.permit-all} est active uniquement par les
 * ressources de test afin de ne pas modifier les tests S2-02 a S2-06, qui ne
 * portent pas sur la securite. La configuration de production ne definit pas
 * cette propriete.</p>
 *
 * <p>S2-09 ajoute une limitation de debit par adresse IP, inseree apres CORS
 * et avant l'authentification : elle couvre le login et les requetes anonymes.
 * Elle est pilotee par {@code gateway.rate-limit.enabled} et
 * {@code gateway.rate-limit.per-minute} (variable {@code RATE_LIMIT_PER_MIN}).</p>
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({SecurityProperties.class, RateLimitProperties.class})
public class SecurityConfiguration {

    private static final int MIN_SECRET_LENGTH = 32;

    @Bean
    public SecretKey jwtSecretKey(SecurityProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (!StringUtils.hasText(secret) || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET est obligatoire et doit contenir au moins "
                            + MIN_SECRET_LENGTH + " caracteres.");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Validation standard : signature, expiration et date de debut.
        decoder.setJwtValidator(JwtValidators.createDefault());
        return decoder;
    }

    /**
     * Compteur de debit local (S2-09). Bean afin de pouvoir etre remis a zero
     * entre les tests d'integration ; aucun endpoint HTTP ne l'expose.
     */
    @Bean
    public RateLimitCounter rateLimitCounter(RateLimitProperties properties) {
        return new RateLimitCounter(properties.getPerMinute());
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            SecurityProperties properties,
            RateLimitProperties rateLimitProperties,
            RateLimitCounter rateLimitCounter,
            ObjectMapper objectMapper,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler) {

        http.csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .requestCache(requestCache -> requestCache.disable())
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .cors(Customizer.withDefaults());

        if (properties.isPermitAll()) {
            http.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll());
            return http.build();
        }

        if (rateLimitProperties.isEnabled()) {
            // S2-09 : apres CORS (place precedemment dans la chaine) et avant
            // AUTHENTICATION, afin que le 429 soit lisible par le navigateur et
            // que les requetes anonymes soient comptees. L'instance est creee
            // ici, sans annotation @Component, pour eviter un double
            // enregistrement par WebFlux.
            http.addFilterBefore(
                    new RateLimitWebFilter(rateLimitCounter, objectMapper),
                    SecurityWebFiltersOrder.AUTHENTICATION);
        }

        http.authorizeExchange(exchanges -> exchanges
                        // Preflight CORS : doit rester accessible sans jeton.
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/api/v1/auth/**").permitAll()

                        // =====================================================
                        // Routes publiques du tableau S2-08.
                        // =====================================================
                        .pathMatchers(HttpMethod.GET,
                                "/api/v1/centres",
                                "/api/v1/centres/*/creneaux",
                                "/api/v1/appels",
                                "/api/v1/certificats/*").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/donneurs").permitAll()

                        // =====================================================
                        // Dons : regles les plus precises d'abord.
                        // Le certificat d'un don est aussi accessible au
                        // donneur concerne : le gateway ne peut verifier que le
                        // role (gap 7 differe), la propriete est deleguee au
                        // service Donneurs.
                        // =====================================================
                        .pathMatchers(HttpMethod.GET, "/api/v1/dons/*/certificat")
                        .hasAnyRole(UserRoles.DONOR, UserRoles.STAFF_CENTRE, UserRoles.ADMIN)
                        .pathMatchers(HttpMethod.GET, "/api/v1/dons/**")
                        .hasAnyRole(UserRoles.STAFF_CENTRE, UserRoles.ADMIN)
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/dons",
                                "/api/v1/dons/*/entretien",
                                "/api/v1/dons/*/prelevement",
                                "/api/v1/dons/*/repos")
                        .hasAnyRole(UserRoles.STAFF_CENTRE, UserRoles.ADMIN)

                        // =====================================================
                        // Ecritures reservees au personnel du centre.
                        // Declarees avant /donneurs/** pour ne pas etre
                        // capturees par la regle DONOR.
                        // =====================================================
                        .pathMatchers(HttpMethod.POST, "/api/v1/donneurs/*/ajournements")
                        .hasAnyRole(UserRoles.STAFF_CENTRE, UserRoles.ADMIN)
                        .pathMatchers(HttpMethod.POST, "/api/v1/centres/*/creneaux")
                        .hasAnyRole(UserRoles.STAFF_CENTRE, UserRoles.ADMIN)

                        // =====================================================
                        // Donneurs et rendez-vous : DONOR (ses propres donnees),
                        // STAFF_CENTRE, ADMIN.
                        // =====================================================
                        .pathMatchers("/api/v1/donneurs/**", "/api/v1/rendez-vous/**")
                        .hasAnyRole(UserRoles.DONOR, UserRoles.STAFF_CENTRE, UserRoles.ADMIN)

                        // =====================================================
                        // Demandes et appels : HOPITAL, ADMIN.
                        // GET /demandes et GET /demandes/{id} ne figurent pas
                        // dans le tableau S2-08 : ils restent authenticated().
                        // =====================================================
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/demandes",
                                "/api/v1/demandes/*/compensations")
                        .hasAnyRole(UserRoles.HOPITAL, UserRoles.ADMIN)
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/demandes/*")
                        .hasAnyRole(UserRoles.HOPITAL, UserRoles.ADMIN)
                        .pathMatchers(HttpMethod.POST, "/api/v1/appels")
                        .hasAnyRole(UserRoles.HOPITAL, UserRoles.ADMIN)
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/appels/*")
                        .hasAnyRole(UserRoles.HOPITAL, UserRoles.ADMIN)

                        // =====================================================
                        // Stock : HOPITAL, STAFF_CENTRE, ADMIN.
                        // =====================================================
                        .pathMatchers(HttpMethod.GET, "/api/v1/stock/**")
                        .hasAnyRole(UserRoles.HOPITAL, UserRoles.STAFF_CENTRE, UserRoles.ADMIN)

                        // Toute autre route exige au moins un jeton valide.
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        // Un jeton present mais invalide (expire, signature
                        // incorrecte) est traite par le filtre Bearer : il faut
                        // lui fournir le meme rendu JSON que le 401 sans jeton.
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(exceptions -> exceptions
                        // 401 : pas de jeton (ou jeton invalide).
                        .authenticationEntryPoint(authenticationEntryPoint)
                        // 403 : jeton valide mais role non autorise.
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    /**
     * Convertit le claim {@code role} en autorite {@code ROLE_<role>}, afin que
     * les regles de S2-08 puissent utiliser {@code hasRole(...)}.
     */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        return jwt -> {
            String role = jwt.getClaimAsString(JwtTokenService.ROLE_CLAIM);
            List<GrantedAuthority> authorities = StringUtils.hasText(role)
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    : List.of();
            return Mono.just(new JwtAuthenticationToken(jwt, authorities, jwt.getSubject()));
        };
    }

    /**
     * Meme politique CORS que {@code spring.cloud.gateway.globalcors} (S2-03),
     * mais appliquee avant le filtre de securite : les reponses 401 doivent
     * aussi porter les en-tetes CORS pour etre lisibles par le navigateur.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000}") String allowedOrigins) {

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("Location", "X-Request-Id"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
