package tn.iit.blood.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
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
import java.io.UncheckedIOException;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests S2-10 des reponses 401, 403 et 200 avec WireMock comme backend.
 *
 * <p>La securite est active ({@code permit-all=false}) : le gateway verifie
 * reellement les JWT. WireMock ne recoit que les requetes autorisees ; les
 * 401 et 403 sont produits par le gateway, sans appel au backend.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "gateway.security.permit-all=false")
class GatewayWireMockSecurityIntegrationTest {

    private static final WireMockServer WIREMOCK =
            WireMockBackend.startDynamicPortServer();

    @DynamicPropertySource
    static void gatewayTargets(DynamicPropertyRegistry registry) {
        String baseUrl = WireMockBackend.baseUrl(WIREMOCK);
        registry.add("DONNEURS_URL", () -> baseUrl);
        registry.add("DEMANDES_URL", () -> baseUrl);
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @LocalServerPort
    private int gatewayPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        WIREMOCK.resetAll();
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
    }

    @Test
    void requestWithoutTokenGets401AndNeverReachesTheBackend() throws Exception {
        EntityExchangeResult<String> result = client.post()
                .uri("/api/v1/dons")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("donneurId", 1))
                .exchange()
                .expectStatus().isEqualTo(401)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        JsonNode json = objectMapper.readTree(result.getResponseBody());
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("path").asText()).isEqualTo("/api/v1/dons");
        assertThat(json.get("requestId").asText()).isNotBlank();

        WIREMOCK.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void wrongRoleGets403AndNeverReachesTheBackend() throws Exception {
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

        JsonNode json = objectMapper.readTree(result.getResponseBody());
        assertThat(json.get("status").asInt()).isEqualTo(403);
        assertThat(json.get("error").asText()).isEqualTo("Forbidden");

        WIREMOCK.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void allowedRoleGets200FromTheWireMockBackend() {
        WIREMOCK.stubFor(post(urlPathEqualTo("/api/v1/dons"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain; charset=UTF-8")
                        .withBody("wiremock-created")));

        client.post()
                .uri("/api/v1/dons")
                .header("Authorization", "Bearer " + login("staff"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("donneurId", 1))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("wiremock-created");

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo("/api/v1/dons")));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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
