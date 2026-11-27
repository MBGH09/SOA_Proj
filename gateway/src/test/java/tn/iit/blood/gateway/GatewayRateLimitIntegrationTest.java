package tn.iit.blood.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import tn.iit.blood.gateway.security.RateLimitCounter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de bout en bout de la limitation de debit S2-09.
 *
 * <p>Le gateway demarre sur un port aleatoire avec la securite active et une
 * limite volontairement basse (3 requetes par minute). Un serveur HTTP simule
 * joue le role des microservices ; il ne s'agit pas des vrais services.</p>
 *
 * <p>Sont couverts : les N requetes autorisees puis le 429, le contrat JSON et
 * ses en-tetes (CORS, {@code X-Request-Id}, {@code Retry-After}), l'exclusion
 * de la sante, du flux SSE et des preflights, la limitation du login, la
 * non-regression des 401/403 de S2-08 sous le quota, et la priorite du 429 sur
 * l'authentification une fois le quota epuise.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "gateway.security.permit-all=false",
                "gateway.rate-limit.enabled=true",
                "gateway.rate-limit.per-minute=3"
        })
class GatewayRateLimitIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    private static final String TOO_MANY_REQUESTS_MESSAGE =
            "Trop de requetes : la limite de la passerelle est atteinte.";

    private static final HttpServer DOWNSTREAM;

    static {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/stock/niveaux/flux",
                    GatewayRateLimitIntegrationTest::stream);
            server.createContext("/", GatewayRateLimitIntegrationTest::echo);
            server.start();
            DOWNSTREAM = server;
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Repond le chemin recu, comme un microservice minimal. */
    private static void echo(HttpExchange exchange) throws IOException {
        byte[] payload = ("proxied:" + exchange.getRequestURI().getPath())
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    /** Simule le flux SSE de S2-04 (reponse non bufferisee cote backend). */
    private static void stream(HttpExchange exchange) throws IOException {
        byte[] payload = "data: stock=1\n\n".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
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
    private RateLimitCounter rateLimitCounter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        // Le compteur est un bean partage par le contexte Spring : il doit
        // repartir de zero avant chaque methode de test.
        rateLimitCounter.reset();
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
    }

    // ------------------------------------------------------------------
    // Limite exacte
    // ------------------------------------------------------------------

    @Test
    void allowsThreeRequestsThenRejectsTheFourth() {
        for (int index = 0; index < 3; index++) {
            client.get()
                    .uri("/api/v1/centres")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("proxied:/api/v1/centres");
        }

        client.get()
                .uri("/api/v1/centres")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    }

    // ------------------------------------------------------------------
    // Contrat JSON et en-tetes du 429
    // ------------------------------------------------------------------

    @Test
    void rejectedResponseUsesTheJsonContractAndKeepsHeaders() throws Exception {
        exhaustQuota();

        EntityExchangeResult<String> result = client.get()
                .uri("/api/v1/centres")
                .header("Origin", ALLOWED_ORIGIN)
                .header("X-Request-Id", "s209-429-contract")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
                .expectHeader().valueEquals("X-Request-Id", "s209-429-contract")
                .expectHeader().exists("Retry-After")
                .expectBody(String.class)
                .returnResult();

        String retryAfter = result.getResponseHeaders().getFirst("Retry-After");
        assertThat(Integer.parseInt(retryAfter)).isBetween(1, 60);

        String body = result.getResponseBody();
        assertThat(body)
                .as("une page HTML ne doit jamais etre renvoyee")
                .doesNotContain("<html")
                .doesNotContain("<!DOCTYPE");

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("timestamp").asText()).isNotBlank();
        assertThat(json.get("status").asInt()).isEqualTo(429);
        assertThat(json.get("error").asText()).isEqualTo("Too Many Requests");
        assertThat(json.get("message").asText()).isEqualTo(TOO_MANY_REQUESTS_MESSAGE);
        assertThat(json.get("path").asText()).isEqualTo("/api/v1/centres");
        assertThat(json.get("requestId").asText()).isEqualTo("s209-429-contract");
    }

    // ------------------------------------------------------------------
    // Exclusions : sante, flux SSE, preflight
    // ------------------------------------------------------------------

    @Test
    void healthEndpointIsNeverLimitedNorCounted() {
        for (int index = 0; index < 8; index++) {
            client.get()
                    .uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk();
        }

        // Le quota est intact : 3 requetes normales passent encore.
        for (int index = 0; index < 3; index++) {
            client.get()
                    .uri("/api/v1/centres")
                    .exchange()
                    .expectStatus().isOk();
        }
        client.get()
                .uri("/api/v1/centres")
                .exchange()
                .expectStatus().isEqualTo(429);
    }

    @Test
    void streamRouteIsNeverLimitedNorCounted() {
        String token = login("staff");
        rateLimitCounter.reset();

        for (int index = 0; index < 6; index++) {
            client.get()
                    .uri("/api/v1/stock/niveaux/flux")
                    .header("Authorization", "Bearer " + token)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                    .expectBody(String.class)
                    .value(body -> assertThat(body).contains("data:"));
        }

        // Les appels au flux n'ont pas consomme le quota.
        for (int index = 0; index < 3; index++) {
            client.get()
                    .uri("/api/v1/centres")
                    .exchange()
                    .expectStatus().isOk();
        }
        client.get()
                .uri("/api/v1/centres")
                .exchange()
                .expectStatus().isEqualTo(429);
    }

    @Test
    void corsPreflightIsNeverLimitedNorCounted() {
        for (int index = 0; index < 6; index++) {
            client.method(HttpMethod.OPTIONS)
                    .uri("/api/v1/dons")
                    .header("Origin", ALLOWED_ORIGIN)
                    .header("Access-Control-Request-Method", "POST")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
        }

        for (int index = 0; index < 3; index++) {
            client.get()
                    .uri("/api/v1/centres")
                    .exchange()
                    .expectStatus().isOk();
        }
        client.get()
                .uri("/api/v1/centres")
                .exchange()
                .expectStatus().isEqualTo(429);
    }

    // ------------------------------------------------------------------
    // Login et non-regression S2-08
    // ------------------------------------------------------------------

    @Test
    void loginIsLimitedToo() {
        for (int index = 0; index < 3; index++) {
            login("donor");
        }

        client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "username", "donor",
                        "password", "test-donor-password"))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectBody()
                .jsonPath("$.status").isEqualTo(429);
    }

    @Test
    void anonymousRequestUnderTheQuotaStillGets401() {
        client.get()
                .uri("/api/v1/rendez-vous")
                .exchange()
                .expectStatus().isEqualTo(401)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    void donorUnderTheQuotaStillGets403OnCreateDonation() {
        String token = login("donor");

        client.post()
                .uri("/api/v1/dons")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("donneurId", 1))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(403);
    }

    @Test
    void onceTheQuotaIsExhaustedThe429PrecedesAuthentication() {
        exhaustQuota();

        // Requete anonyme vers une route protegee : le filtre de limitation
        // s'execute avant l'authentification, donc 429 et non 401.
        client.get()
                .uri("/api/v1/rendez-vous")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectBody()
                .jsonPath("$.status").isEqualTo(429);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void exhaustQuota() {
        for (int index = 0; index < 3; index++) {
            client.get()
                    .uri("/api/v1/centres")
                    .exchange()
                    .expectStatus().isOk();
        }
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
}
