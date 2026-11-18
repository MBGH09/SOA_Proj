package tn.iit.blood.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de bout en bout des timeouts S2-04.
 *
 * <p>Un serveur HTTP simule (JDK {@link HttpServer}) retarde toutes ses reponses
 * au-dela du timeout global configure ci-dessous. Le flux SSE
 * {@code /api/v1/stock/niveaux/flux} beneficie en revanche d'un
 * {@code response-timeout} negatif dans les metadonnees de sa route : il doit
 * donc aboutir et diffuser ses evenements, tandis que les autres routes
 * repondent 504. Il ne s'agit pas d'un test contre les vrais microservices.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "GATEWAY_CONNECT_TIMEOUT_MS=1000",
                "GATEWAY_RESPONSE_TIMEOUT_MS=600"
        })
class GatewayTimeoutIntegrationTest {

    private static final int UPSTREAM_DELAY_MS = 1200;

    private static final ExecutorService UPSTREAM_EXECUTOR = Executors.newCachedThreadPool();

    private static final HttpServer DOWNSTREAM;

    static {
        try {
            DOWNSTREAM = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            DOWNSTREAM.setExecutor(UPSTREAM_EXECUTOR);
            DOWNSTREAM.createContext("/", exchange -> {
                try {
                    String path = exchange.getRequestURI().getPath();
                    if ("/api/v1/stock/niveaux/flux".equals(path)) {
                        // Le debut de la reponse arrive apres le timeout global :
                        // seule la metadonnee response-timeout=-1 permet d'aboutir.
                        sleep(UPSTREAM_DELAY_MS);
                        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=UTF-8");
                        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
                        exchange.sendResponseHeaders(200, 0);
                        try (OutputStream output = exchange.getResponseBody()) {
                            writeEvent(output, "un");
                            sleep(900);
                            writeEvent(output, "deux");
                        }
                    } else {
                        sleep(UPSTREAM_DELAY_MS);
                        byte[] body = "late".getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream output = exchange.getResponseBody()) {
                            output.write(body);
                        }
                    }
                } catch (IOException | RuntimeException ignored) {
                    // Le gateway a pu fermer la connexion apres un timeout : normal.
                }
            });
            DOWNSTREAM.start();
        } catch (IOException exception) {
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
        UPSTREAM_EXECUTOR.shutdownNow();
    }

    @LocalServerPort
    private int gatewayPort;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
    }

    @Test
    void donneursRouteTimesOutWhenTheBackendIsTooSlow() {
        client.get()
                .uri("/api/v1/donneurs")
                .exchange()
                .expectStatus().isEqualTo(504);
    }

    @Test
    void generalStockRouteKeepsTheGlobalResponseTimeout() {
        client.get()
                .uri("/api/v1/stock")
                .exchange()
                .expectStatus().isEqualTo(504);
    }

    @Test
    void stockFluxRouteSurvivesBeyondTheGlobalTimeout() {
        long start = System.nanoTime();

        client.get()
                .uri("/api/v1/stock/niveaux/flux")
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .consumeWith(result -> {
                    String body = new String(result.getResponseBody(), StandardCharsets.UTF_8);
                    assertThat(body).contains("data: un", "data: deux");
                });

        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertThat(elapsedMs)
                .as("le flux doit rester ouvert plus longtemps que le timeout global de 600 ms")
                .isGreaterThanOrEqualTo(1500L);
    }

    private static void writeEvent(OutputStream output, String payload) throws IOException {
        output.write(("data: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
