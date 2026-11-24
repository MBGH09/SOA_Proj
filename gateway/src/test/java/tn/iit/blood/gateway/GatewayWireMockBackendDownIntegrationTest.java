package tn.iit.blood.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Test S2-10 de la reponse 503 lorsque le backend WireMock est arrete.
 *
 * <p>Le serveur ecoute sur un port fixe alloue dynamiquement au chargement de
 * la classe : il peut ainsi etre arrete puis redemarre sur le meme port, alors
 * que l'URL injectee dans le gateway est figee au demarrage du contexte.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "GATEWAY_CONNECT_TIMEOUT_MS=500")
class GatewayWireMockBackendDownIntegrationTest {

    private static final int WIREMOCK_PORT = WireMockBackend.freePort();

    private static final WireMockServer WIREMOCK =
            WireMockBackend.startOnPort(WIREMOCK_PORT);

    @DynamicPropertySource
    static void gatewayTargets(DynamicPropertyRegistry registry) {
        String baseUrl = WireMockBackend.baseUrl(WIREMOCK);
        registry.add("DONNEURS_URL", () -> baseUrl);
        registry.add("DEMANDES_URL", () -> baseUrl);
    }

    @AfterAll
    static void stopWireMock() {
        if (WIREMOCK.isRunning()) {
            WIREMOCK.stop();
        }
    }

    @LocalServerPort
    private int gatewayPort;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        if (!WIREMOCK.isRunning()) {
            WIREMOCK.start();
        }
        WIREMOCK.resetAll();
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
    }

    @Test
    void stoppedBackendProduces503AndRecoversAfterRestart() {
        stubOk("/api/v1/donneurs/42");

        client.get()
                .uri("/api/v1/donneurs/42")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("wiremock:ok");

        WIREMOCK.stop();

        try {
            client.get()
                    .uri("/api/v1/donneurs/42")
                    .exchange()
                    .expectStatus().isEqualTo(503)
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(503)
                    .jsonPath("$.error").isEqualTo("Service Unavailable")
                    .jsonPath("$.path").isEqualTo("/api/v1/donneurs/42");
        }
        finally {
            WIREMOCK.start();
        }

        // Apres redemarrage, la meme route redevient disponible : le 503
        // n'etait pas un etat definitif du gateway.
        stubOk("/api/v1/donneurs/42");
        client.get()
                .uri("/api/v1/donneurs/42")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("wiremock:ok");

        WIREMOCK.verify(getRequestedFor(urlPathEqualTo("/api/v1/donneurs/42")));
    }

    private void stubOk(String path) {
        WIREMOCK.stubFor(get(urlPathEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain; charset=UTF-8")
                        .withBody("wiremock:ok")));
    }
}
