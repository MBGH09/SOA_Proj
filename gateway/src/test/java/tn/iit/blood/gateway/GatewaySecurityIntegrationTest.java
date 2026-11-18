package tn.iit.blood.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de bout en bout de la securite S2-07.
 *
 * <p>Le gateway demarre reellement sur un port aleatoire, avec la securite
 * active (pas de mode permit-all). Un serveur HTTP simule joue le role des
 * microservices pour verifier qu'une requete authentifiee atteint bien le
 * backend. Il ne s'agit pas des vrais microservices.</p>
 *
 * <p>Sont couverts : la connexion des quatre utilisateurs, les claims et la
 * duree du JWT, le 401 JSON sans jeton, le 400/401 de la connexion, le rejet
 * d'un jeton expire ou falsifie, les routes publiques de S2-08 et le preflight
 * CORS.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "gateway.security.permit-all=false")
class GatewaySecurityIntegrationTest {

    private static final String DONOR_USERNAME = "donor";
    private static final String DONOR_PASSWORD = "test-donor-password";

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    private static final HttpServer DOWNSTREAM;

    static {
        try {
            DOWNSTREAM = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            DOWNSTREAM.createContext("/", exchange -> {
                byte[] body = ("proxied:" + exchange.getRequestURI().getPath())
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            DOWNSTREAM.start();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
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
    // Connexion et JWT
    // ------------------------------------------------------------------

    @Test
    void loginReturnsHs256TokenWithOfficialClaimsAndOneHourTtl() throws Exception {
        String token = login(DONOR_USERNAME, DONOR_PASSWORD);

        JsonNode header = tokenPart(token, 0);
        JsonNode payload = tokenPart(token, 1);

        assertThat(header.get("alg").asText()).isEqualTo("HS256");
        assertThat(payload.get("sub").asText()).isEqualTo(DONOR_USERNAME);
        assertThat(payload.get("role").asText()).isEqualTo("DONOR");
        assertThat(payload.has("iat")).isTrue();
        assertThat(payload.has("exp")).isTrue();
        assertThat(payload.get("exp").asLong() - payload.get("iat").asLong())
                .as("le jeton doit etre valable une heure")
                .isEqualTo(3600L);
        assertThat(payload.has("donneurId"))
                .as("gap 7 differe : aucun claim donneurId")
                .isFalse();
        assertThat(payload.has("hopitalId"))
                .as("gap 7 differe : aucun claim hopitalId")
                .isFalse();
    }

    @Test
    void everyDemoUserCanLogInWithItsOfficialRole() throws Exception {
        Map<String, String> expectedRoles = Map.of(
                "donor", "DONOR",
                "staff", "STAFF_CENTRE",
                "hopital", "HOPITAL",
                "admin", "ADMIN");

        for (Map.Entry<String, String> expected : expectedRoles.entrySet()) {
            String password = "test-" + expected.getKey() + "-password";
            String token = login(expected.getKey(), password);

            assertThat(tokenPart(token, 1).get("role").asText())
                    .as("role du compte " + expected.getKey())
                    .isEqualTo(expected.getValue());
        }
    }

    @Test
    void validDonorTokenReachesTheProtectedBackend() {
        String token = login(DONOR_USERNAME, DONOR_PASSWORD);

        client.get()
                .uri("/api/v1/rendez-vous")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("proxied:/api/v1/rendez-vous");
    }

    @Test
    void requestWithoutTokenReturnsJson401() throws Exception {
        EntityExchangeResult<String> result = client.get()
                .uri("/api/v1/rendez-vous")
                .exchange()
                .expectStatus().isEqualTo(401)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().value("WWW-Authenticate", value -> assertThat(value).contains("Bearer"))
                .expectBody(String.class)
                .returnResult();

        JsonNode json = assertJsonError(result, 401, "/api/v1/rendez-vous");
        assertThat(json.get("message").asText()).isEqualTo("Un jeton d'acces valide est requis.");
        assertThat(json.get("requestId").asText())
                .isEqualTo(result.getResponseHeaders().getFirst("X-Request-Id"))
                .isNotBlank();
    }

    @Test
    void wrongPasswordReturnsJson401WithoutRevealingWhetherTheUserExists() throws Exception {
        EntityExchangeResult<String> result = loginExpecting(401, DONOR_USERNAME, "mauvais-mot-de-passe");

        JsonNode json = assertJsonError(result, 401, "/api/v1/auth/login");
        assertThat(json.get("message").asText()).isEqualTo("Identifiants invalides.");
    }

    @Test
    void unknownUserReturnsJson401() throws Exception {
        EntityExchangeResult<String> result = loginExpecting(401, "inconnu", "quelque-chose");

        JsonNode json = assertJsonError(result, 401, "/api/v1/auth/login");
        assertThat(json.get("message").asText()).isEqualTo("Identifiants invalides.");
    }

    @Test
    void missingCredentialsReturnJson400() throws Exception {
        EntityExchangeResult<String> result = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "donor"))
                .exchange()
                .expectStatus().isEqualTo(400)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        JsonNode json = assertJsonError(result, 400, "/api/v1/auth/login");
        assertThat(json.get("message").asText())
                .isEqualTo("Nom d'utilisateur et mot de passe obligatoires.");
    }

    @Test
    void expiredTokenIsRejectedWithJson401() throws Exception {
        Instant now = Instant.now();
        String expired = token(DONOR_USERNAME, "DONOR", now.minusSeconds(7200), now.minusSeconds(3600));

        EntityExchangeResult<String> result = client.get()
                .uri("/api/v1/rendez-vous")
                .header("Authorization", "Bearer " + expired)
                .exchange()
                .expectStatus().isEqualTo(401)
                .expectBody(String.class)
                .returnResult();

        assertJsonError(result, 401, "/api/v1/rendez-vous");
    }

    @Test
    void tamperedTokenIsRejectedWithJson401() throws Exception {
        String token = login(DONOR_USERNAME, DONOR_PASSWORD);
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("AA") ? "BB" : "AA");

        EntityExchangeResult<String> result = client.get()
                .uri("/api/v1/rendez-vous")
                .header("Authorization", "Bearer " + tampered)
                .exchange()
                .expectStatus().isEqualTo(401)
                .expectBody(String.class)
                .returnResult();

        assertJsonError(result, 401, "/api/v1/rendez-vous");
    }

    // ------------------------------------------------------------------
    // Routes publiques et CORS
    // ------------------------------------------------------------------

    @Test
    void publicRoutesReachTheBackendWithoutToken() {
        for (String path : List.of(
                "/api/v1/centres",
                "/api/v1/centres/1/creneaux",
                "/api/v1/appels",
                "/api/v1/certificats/CNTS-2026-0001")) {
            client.get()
                    .uri(path)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("proxied:" + path);
        }

        client.post()
                .uri("/api/v1/donneurs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nom", "Test"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("proxied:/api/v1/donneurs");

        client.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void protectedRouteStillRequiresAToken() {
        client.post()
                .uri("/api/v1/demandes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .exchange()
                .expectStatus().isEqualTo(401);
    }

    @Test
    void preflightIsAcceptedWithoutToken() {
        client.options()
                .uri("/api/v1/rendez-vous")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
                .expectHeader().value("Access-Control-Allow-Headers",
                        value -> assertThat(value.toLowerCase()).contains("authorization"));
    }

    @Test
    void unauthorizedResponseCarriesCorsHeaders() {
        client.get()
                .uri("/api/v1/rendez-vous")
                .header("Origin", ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isEqualTo(401)
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
    }

    @Test
    void unknownRouteNeedsATokenThenReturnsJson404() throws Exception {
        client.get()
                .uri("/api/v1/route-inconnue")
                .exchange()
                .expectStatus().isEqualTo(401);

        String token = login(DONOR_USERNAME, DONOR_PASSWORD);

        EntityExchangeResult<String> result = client.get()
                .uri("/api/v1/route-inconnue")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(404)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        JsonNode json = assertJsonError(result, 404, "/api/v1/route-inconnue");
        assertThat(json.get("error").asText()).isEqualTo("Not Found");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String login(String username, String password) {
        EntityExchangeResult<String> result = loginExpecting(200, username, password);
        return readToken(result.getResponseBody());
    }

    private EntityExchangeResult<String> loginExpecting(int status,
                                                        String username,
                                                        String password) {
        return client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", username, "password", password))
                .exchange()
                .expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();
    }

    private String readToken(String body) {
        try {
            return objectMapper.readTree(body).get("token").asText();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private JsonNode tokenPart(String token, int index) throws IOException {
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        byte[] decoded = Base64.getUrlDecoder().decode(parts[index]);
        return objectMapper.readTree(decoded);
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
        return json;
    }
}
