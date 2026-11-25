package tn.iit.blood.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de bout en bout du format JSON des erreurs (S2-06).
 *
 * <p>Le gateway demarre reellement sur un port aleatoire. Deux situations
 * amont sont simulees :</p>
 * <ul>
 *   <li>un port <b>ferme</b> pour Donneurs, afin de provoquer une connexion
 *       refusee (service arrete) ;</li>
 *   <li>un serveur HTTP <b>simule</b> et lent pour Demandes, afin de depasser
 *       le timeout de reponse configure.</li>
 * </ul>
 *
 * <p>Une route inexistante verifie le cas 404. Il ne s'agit pas de tests contre
 * les vrais microservices.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "GATEWAY_CONNECT_TIMEOUT_MS=800",
                "GATEWAY_RESPONSE_TIMEOUT_MS=400"
        })
class GatewayErrorHandlingIntegrationTest {

    private static final int SLOW_UPSTREAM_DELAY_MS = 1500;

    private static final HttpServer SLOW_UPSTREAM;

    private static final int CLOSED_PORT;

    static {
        try {
            SLOW_UPSTREAM = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SLOW_UPSTREAM.setExecutor(Executors.newCachedThreadPool());
            SLOW_UPSTREAM.createContext("/", exchange -> {
                try {
                    Thread.sleep(SLOW_UPSTREAM_DELAY_MS);
                    byte[] body = "late".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                }
                catch (IOException ignored) {
                    // Le gateway a ferme la connexion apres le timeout : normal.
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            SLOW_UPSTREAM.start();

            CLOSED_PORT = findClosedPortExcluding(SLOW_UPSTREAM.getAddress().getPort());
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Retourne un port libre qui n'est plus ecoute : toute connexion y sera
     * refusee. Le port du serveur lent est exclu pour eviter toute collision.
     */
    private static int findClosedPortExcluding(int excludedPort) throws IOException {
        for (int attempt = 0; attempt < 20; attempt++) {
            try (ServerSocket socket = new ServerSocket(0)) {
                int port = socket.getLocalPort();
                if (port != excludedPort) {
                    return port;
                }
            }
        }
        throw new IllegalStateException("Impossible de trouver un port libre pour le test");
    }

    @DynamicPropertySource
    static void gatewayTargets(DynamicPropertyRegistry registry) {
        registry.add("DONNEURS_URL", () -> "http://127.0.0.1:" + CLOSED_PORT);
        registry.add("DEMANDES_URL",
                () -> "http://127.0.0.1:" + SLOW_UPSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stopUpstream() {
        SLOW_UPSTREAM.stop(0);
    }

    @LocalServerPort
    private int gatewayPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
    }

    private JsonNode getJson(String path, int expectedStatus) throws Exception {
        EntityExchangeResult<String> result = client.get()
                .uri(path)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

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
        assertThat(json.get("path").asText()).isEqualTo(path);
        return json;
    }

    @Test
    void stoppedServiceReturnsJson503() throws Exception {
        EntityExchangeResult<String> result = client.get()
                .uri("/api/v1/donneurs")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        String body = result.getResponseBody();
        assertThat(body).isNotNull();
        assertThat(body)
                .as("un service arrete doit produire du JSON, jamais du HTML")
                .doesNotContain("<html")
                .doesNotContain("<!DOCTYPE");

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("status").asInt()).isEqualTo(503);
        assertThat(json.get("error").asText()).isEqualTo("Service Unavailable");
        assertThat(json.get("message").asText()).isNotBlank();
        assertThat(json.get("path").asText()).isEqualTo("/api/v1/donneurs");
        assertThat(json.get("timestamp").asText()).isNotBlank();

        // Le filtre S2-05 a tourne (route existante) : l'identifiant doit etre
        // present dans l'en-tete de reponse et repris dans le corps JSON.
        String requestId = result.getResponseHeaders().getFirst("X-Request-Id");
        assertThat(requestId).isNotBlank();
        assertThat(json.get("requestId").asText()).isEqualTo(requestId);
    }

    @Test
    void responseTimeoutReturnsJson504() throws Exception {
        JsonNode json = getJson("/api/v1/demandes", 504);

        assertThat(json.get("error").asText()).isEqualTo("Gateway Timeout");
        assertThat(json.get("status").asInt()).isEqualTo(504);
    }

    @Test
    void unknownRouteReturnsJson404() throws Exception {
        JsonNode json = getJson("/api/v1/route-inconnue", 404);

        assertThat(json.get("error").asText()).isEqualTo("Not Found");
        assertThat(json.get("status").asInt()).isEqualTo(404);
    }
}
