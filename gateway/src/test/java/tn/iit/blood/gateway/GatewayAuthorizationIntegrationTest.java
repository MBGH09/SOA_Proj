package tn.iit.blood.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de bout en bout des regles de role de S2-08.
 *
 * <p>Le gateway demarre reellement sur un port aleatoire, securite active
 * (pas de mode permit-all). Un serveur HTTP simule joue le role des
 * microservices : il renvoie le chemin recu et les en-tetes d'identite
 * effectivement transmis. Il ne s'agit pas des vrais microservices.</p>
 *
 * <p>Sont couverts : la matrice complete du tableau officiel (chaque route,
 * chaque role autorise et interdit), le critere « un jeton donneur recoit 403
 * sur POST /dons et un jeton staff recoit 200 », le 403 JSON (contrat, absence
 * de HTML, absence de WWW-Authenticate, CORS, X-Request-Id), le 401 anonyme et
 * la suppression/reinjection des en-tetes X-User-Id et X-User-Role.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "gateway.security.permit-all=false")
class GatewayAuthorizationIntegrationTest {

    private static final String DONOR = "DONOR";
    private static final String STAFF = "STAFF_CENTRE";
    private static final String HOPITAL = "HOPITAL";
    private static final String ADMIN = "ADMIN";

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    private static final String FORBIDDEN_MESSAGE =
            "Acces refuse : le role de l'utilisateur ne permet pas cette operation.";

    private static final HttpServer DOWNSTREAM;

    static {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", GatewayAuthorizationIntegrationTest::echo);
            server.start();
            DOWNSTREAM = server;
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Repond le chemin recu et les en-tetes d'identite vus par le backend. */
    private static void echo(HttpExchange exchange) throws IOException {
        String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
        String role = exchange.getRequestHeaders().getFirst("X-User-Role");
        String body = "proxied:" + exchange.getRequestURI().getPath()
                + "|userId=" + (userId == null ? "none" : userId)
                + "|role=" + (role == null ? "none" : role);
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    @DynamicPropertySource
    static void gatewayTargets(DynamicPropertyRegistry registry) {
        String baseUrl = "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort();
        registry.add("DONNEURS_URL", () -> baseUrl);
        registry.add("DEMANDES_URL", () -> baseUrl);
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
    }

    // ------------------------------------------------------------------
    // Matrice officielle : chaque route, chaque role
    // ------------------------------------------------------------------

    static Stream<Arguments> authorizationMatrix() {
        List<Rule> rules = List.of(
                // Routes publiques (aucune restriction de role) : verifiees a
                // part, sans jeton, dans les tests de non-regression.
                new Rule(HttpMethod.POST, "/api/v1/dons", Set.of(STAFF, ADMIN)),
                new Rule(HttpMethod.POST, "/api/v1/dons/42/entretien", Set.of(STAFF, ADMIN)),
                new Rule(HttpMethod.POST, "/api/v1/dons/42/prelevement", Set.of(STAFF, ADMIN)),
                new Rule(HttpMethod.POST, "/api/v1/dons/42/repos", Set.of(STAFF, ADMIN)),
                new Rule(HttpMethod.GET, "/api/v1/dons/42", Set.of(STAFF, ADMIN)),
                new Rule(HttpMethod.GET, "/api/v1/dons/42/certificat", Set.of(DONOR, STAFF, ADMIN)),
                new Rule(HttpMethod.POST, "/api/v1/donneurs/7/ajournements", Set.of(STAFF, ADMIN)),
                new Rule(HttpMethod.POST, "/api/v1/centres/1/creneaux", Set.of(STAFF, ADMIN)),
                new Rule(HttpMethod.GET, "/api/v1/donneurs/7", Set.of(DONOR, STAFF, ADMIN)),
                new Rule(HttpMethod.GET, "/api/v1/rendez-vous", Set.of(DONOR, STAFF, ADMIN)),
                new Rule(HttpMethod.POST, "/api/v1/rendez-vous", Set.of(DONOR, STAFF, ADMIN)),
                new Rule(HttpMethod.POST, "/api/v1/demandes", Set.of(HOPITAL, ADMIN)),
                new Rule(HttpMethod.PATCH, "/api/v1/demandes/9", Set.of(HOPITAL, ADMIN)),
                new Rule(HttpMethod.POST, "/api/v1/demandes/9/compensations", Set.of(HOPITAL, ADMIN)),
                new Rule(HttpMethod.POST, "/api/v1/appels", Set.of(HOPITAL, ADMIN)),
                new Rule(HttpMethod.PATCH, "/api/v1/appels/3", Set.of(HOPITAL, ADMIN)),
                new Rule(HttpMethod.GET, "/api/v1/stock/niveaux", Set.of(HOPITAL, STAFF, ADMIN)));

        return rules.stream().flatMap(rule -> Stream.of(DONOR, STAFF, HOPITAL, ADMIN)
                .map(role -> Arguments.of(
                        rule.method.name() + " " + rule.path,
                        rule.method,
                        rule.path,
                        role,
                        rule.allowedRoles.contains(role))));
    }

    @ParameterizedTest(name = "{0} avec le role {3} -> {4}")
    @MethodSource("authorizationMatrix")
    void roleMatrixMatchesTheOfficialTable(String label,
                                           HttpMethod method,
                                           String path,
                                           String role,
                                           boolean allowed) {
        String token = tokenForRole(role);

        WebTestClient.RequestBodySpec request = client.method(method)
                .uri(path)
                .header("Authorization", "Bearer " + token);

        if (method == HttpMethod.POST || method == HttpMethod.PATCH) {
            request = request.contentType(MediaType.APPLICATION_JSON);
        }

        if (allowed) {
            request.exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("proxied:" + path + "|userId=" + usernameForRole(role)
                            + "|role=" + role);
        }
        else {
            request.exchange()
                    .expectStatus().isForbidden()
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        }
    }

    // ------------------------------------------------------------------
    // Critere officiel S2-08
    // ------------------------------------------------------------------

    @Test
    void donorTokenGets403OnCreateDonation() throws Exception {
        EntityExchangeResult<String> result = client.post()
                .uri("/api/v1/dons")
                .header("Authorization", "Bearer " + login("donor"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("donneurId", 1))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().doesNotExist("WWW-Authenticate")
                .expectBody(String.class)
                .returnResult();

        JsonNode json = assertJsonError(result, 403, "/api/v1/dons");
        assertThat(json.get("error").asText()).isEqualTo("Forbidden");
        assertThat(json.get("message").asText()).isEqualTo(FORBIDDEN_MESSAGE);
    }

    @Test
    void staffTokenGets200OnCreateDonation() {
        client.post()
                .uri("/api/v1/dons")
                .header("Authorization", "Bearer " + login("staff"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("donneurId", 1))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("proxied:/api/v1/dons|userId=staff|role=STAFF_CENTRE");
    }

    // ------------------------------------------------------------------
    // 401 / 403
    // ------------------------------------------------------------------

    @Test
    void anonymousRequestsReceiveJson401OnRoleProtectedRoutes() {
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.GET, HttpMethod.PATCH)) {
            client.method(method)
                    .uri("/api/v1/dons")
                    .exchange()
                    .expectStatus().isEqualTo(401)
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(401);
        }
    }

    @Test
    void forbiddenResponseKeepsCorsAndRequestIdHeaders() throws Exception {
        EntityExchangeResult<String> result = client.get()
                .uri("/api/v1/stock/niveaux")
                .header("Authorization", "Bearer " + login("donor"))
                .header("Origin", ALLOWED_ORIGIN)
                .header("X-Request-Id", "s208-forbidden-cors")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
                .expectHeader().valueEquals("X-Request-Id", "s208-forbidden-cors")
                .expectBody(String.class)
                .returnResult();

        JsonNode json = assertJsonError(result, 403, "/api/v1/stock/niveaux");
        assertThat(json.get("requestId").asText()).isEqualTo("s208-forbidden-cors");
    }

    @Test
    void tokenWithUnknownRoleIsForbiddenNotUnauthorized() {
        Instant now = Instant.now();
        String token = token("inconnu", "CHEF", now, now.plusSeconds(3600));

        client.get()
                .uri("/api/v1/stock/niveaux")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    }

    @Test
    void expiredOrTamperedTokenIsStillRejectedBeforeTheRoleCheck() {
        String expired = token("donor", DONOR,
                Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

        client.post()
                .uri("/api/v1/dons")
                .header("Authorization", "Bearer " + expired)
                .exchange()
                .expectStatus().isEqualTo(401);

        client.post()
                .uri("/api/v1/dons")
                .header("Authorization", "Bearer pas-un-jeton")
                .exchange()
                .expectStatus().isEqualTo(401);
    }

    // ------------------------------------------------------------------
    // En-tetes d'identite
    // ------------------------------------------------------------------

    @Test
    void identityHeadersAreReplacedFromTheVerifiedToken() {
        client.get()
                .uri("/api/v1/rendez-vous")
                .header("Authorization", "Bearer " + login("donor"))
                .header("X-User-Id", "admin")
                .header("X-User-Role", ADMIN)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("proxied:/api/v1/rendez-vous|userId=donor|role=DONOR");
    }

    @Test
    void identityHeadersAreRemovedOnPublicRoutes() {
        client.get()
                .uri("/api/v1/centres")
                .header("X-User-Id", "admin")
                .header("X-User-Role", ADMIN)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("proxied:/api/v1/centres|userId=none|role=none");
    }

    @Test
    void identityHeadersAreRemovedWhenTheTokenIsRejected() {
        // Le 401 est produit par Spring Security avant les filtres du gateway :
        // le backend ne recoit rien, mais la reponse ne doit pas renvoyer les
        // en-tetes falsifies non plus.
        client.get()
                .uri("/api/v1/rendez-vous")
                .header("X-User-Id", "admin")
                .header("X-User-Role", ADMIN)
                .exchange()
                .expectStatus().isEqualTo(401)
                .expectHeader().doesNotExist("X-User-Id")
                .expectHeader().doesNotExist("X-User-Role");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String tokenForRole(String role) {
        return switch (role) {
            case DONOR -> login("donor");
            case STAFF -> login("staff");
            case HOPITAL -> login("hopital");
            case ADMIN -> login("admin");
            default -> throw new IllegalArgumentException("role inconnu : " + role);
        };
    }

    private String usernameForRole(String role) {
        return switch (role) {
            case DONOR -> "donor";
            case STAFF -> "staff";
            case HOPITAL -> "hopital";
            case ADMIN -> "admin";
            default -> throw new IllegalArgumentException("role inconnu : " + role);
        };
    }

    private String login(String username) {
        EntityExchangeResult<String> result = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "username", username,
                        "password", "test-" + username + "-password"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        try {
            return objectMapper.readTree(result.getResponseBody()).get("token").asText();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private String token(String subject, String role, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", role)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private JsonNode assertJsonError(EntityExchangeResult<String> result,
                                     int expectedStatus,
                                     String expectedPath) throws IOException {
        String body = result.getResponseBody();
        assertThat(body).isNotNull();
        assertThat(body)
                .as("une page HTML ne doit jamais etre renvoyee")
                .doesNotContain("<html")
                .doesNotContain("<!DOCTYPE");

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("timestamp").asText()).isNotBlank();
        assertThat(json.get("status").asInt()).isEqualTo(expectedStatus);
        assertThat(json.get("error").asText()).isNotBlank();
        assertThat(json.get("message").asText()).isNotBlank();
        assertThat(json.get("path").asText()).isEqualTo(expectedPath);
        assertThat(json.get("requestId").asText())
                .isEqualTo(result.getResponseHeaders().getFirst("X-Request-Id"))
                .isNotBlank();
        return json;
    }

    /** Une ligne du tableau officiel S2-08. */
    private record Rule(HttpMethod method, String path, Set<String> allowedRoles) {
    }
}
