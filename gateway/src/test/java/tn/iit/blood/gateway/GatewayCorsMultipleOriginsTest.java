package tn.iit.blood.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

/**
 * Verifie que {@code CORS_ALLOWED_ORIGINS} accepte plusieurs origines separees
 * par des virgules (S2-03). Le gateway demarre sur un port aleatoire et repond
 * au preflight sans appeler de microservice.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:4200")
class GatewayCorsMultipleOriginsTest {

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
    void everyConfiguredOriginIsAllowed() {
        for (String origin : List.of("http://localhost:3000", "http://localhost:4200")) {
            client.options()
                    .uri("/api/v1/stock")
                    .header("Origin", origin)
                    .header("Access-Control-Request-Method", "GET")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("Access-Control-Allow-Origin", origin);
        }
    }

    @Test
    void originOutsideTheListIsRejected() {
        client.options()
                .uri("/api/v1/stock")
                .header("Origin", "http://localhost:5000")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin");
    }
}
