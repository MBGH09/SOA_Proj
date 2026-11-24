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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests CORS de bout en bout S2-03.
 *
 * <p>Un serveur HTTP simule (JDK {@link HttpServer}) joue le role des microservices
 * Donneurs et Demandes : il repond {@code proxied:<chemin>}. Le gateway demarre sur
 * un port aleatoire et applique la configuration {@code globalcors} reelle de
 * {@code application.yml}. On verifie le preflight (OPTIONS) et les en-tetes CORS
 * d'une reponse reelle. Il ne s'agit pas d'un test contre les vrais microservices.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayCorsIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    private static final HttpServer DOWNSTREAM;

    static {
        try {
            DOWNSTREAM = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            DOWNSTREAM.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                byte[] body = ("proxied:" + path).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
                exchange.getResponseHeaders().add("Location", "/api/v1/donneurs/1");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
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
    void preflightFromAllowedOriginIsAccepted() {
        client.options()
                .uri("/api/v1/donneurs")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
                .expectHeader().value("Access-Control-Allow-Methods",
                        value -> assertThat(value).contains("POST"))
                .expectHeader().value("Access-Control-Allow-Headers",
                        value -> assertThat(value.toLowerCase()).contains("content-type", "authorization"));
    }

    @Test
    void preflightForEveryAllowedMethodIsAccepted() {
        for (String method : new String[]{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"}) {
            client.options()
                    .uri("/api/v1/demandes")
                    .header("Origin", ALLOWED_ORIGIN)
                    .header("Access-Control-Request-Method", method)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
                    .expectHeader().value("Access-Control-Allow-Methods",
                            value -> assertThat(value).contains(method));
        }
    }

    @Test
    void preflightFromDisallowedOriginIsRejected() {
        client.options()
                .uri("/api/v1/donneurs")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin");
    }

    @Test
    void proxiedResponseExposesTheConfiguredHeaders() {
        client.get()
                .uri("/api/v1/donneurs")
                .header("Origin", ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("proxied:/api/v1/donneurs"))
                .consumeWith(result -> {
                    assertThat(result.getResponseHeaders().getFirst("Access-Control-Allow-Origin"))
                            .isEqualTo(ALLOWED_ORIGIN);
                    String exposed = result.getResponseHeaders().getFirst("Access-Control-Expose-Headers");
                    assertThat(exposed).isNotNull();
                    assertThat(exposed).contains("Location", "X-Request-Id");
                });
    }
}
