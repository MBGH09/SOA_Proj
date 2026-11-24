package tn.iit.blood.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Test de routage de bout en bout S2-02.
 *
 * <p>Un serveur HTTP simule (JDK {@link HttpServer}) joue le role des microservices
 * Donneurs et Demandes : il repond {@code proxied:<chemin>}. Le gateway demarre sur
 * un port aleatoire et les deux variables d'environnement pointent vers ce serveur.
 * On verifie ainsi que chaque chemin configure est reellement transmis tel quel
 * (sans retrait de prefixe), sans pretendre avoir teste les vrais microservices.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {

    private static final HttpServer DOWNSTREAM;

    static {
        try {
            DOWNSTREAM = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            DOWNSTREAM.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                byte[] body = ("proxied:" + path).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
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

    @ParameterizedTest(name = "GET {0} est transmis au microservice cible")
    @ValueSource(strings = {
            "/api/v1/donneurs",
            "/api/v1/donneurs/42",
            "/api/v1/centres",
            "/api/v1/centres/5",
            "/api/v1/centres/5/creneaux",
            "/api/v1/rendez-vous",
            "/api/v1/dons/3",
            "/api/v1/certificats/9",
            "/api/v1/demandes",
            "/api/v1/demandes/1",
            "/api/v1/appels",
            "/api/v1/stock"
    })
    void forwardsRequestToTargetService(String path) {
        client.get()
                .uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("proxied:" + path));
    }
}
