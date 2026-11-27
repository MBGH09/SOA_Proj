package tn.iit.blood.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de bout en bout du gateway avec WireMock comme backend simule (S2-10).
 *
 * <p>WireMock joue le role des microservices Donneurs et Demandes ; il ne
 * s'agit pas des vrais services. Deux serveurs distincts sont utilises afin de
 * verifier non seulement le chemin transmis, mais aussi le service cible de
 * chaque route. Le rate limiting est desactive par
 * {@code src/test/resources/application.properties}, et la securite est en
 * mode {@code permit-all} : cette classe porte sur le routage, le CORS, les
 * timeouts, le {@code X-Request-Id} et le streaming. Les regles de role sont
 * verifiees dans {@link GatewayWireMockSecurityIntegrationTest}.</p>
 *
 * <p>Le timeout de reponse est volontairement court (800 ms) pour que le test
 * 504 reste rapide. La route SSE conserve son {@code response-timeout: -1} et
 * doit donc diffuser au-dela de ce delai.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "GATEWAY_CONNECT_TIMEOUT_MS=1000",
                "GATEWAY_RESPONSE_TIMEOUT_MS=800"
        })
class GatewayWireMockRoutingIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    /** Duree totale du flux dribble : bien superieure au timeout global. */
    private static final int STREAM_DURATION_MS = 2400;

    private static final WireMockServer DONNEURS =
            WireMockBackend.startDynamicPortServer();

    private static final WireMockServer DEMANDES =
            WireMockBackend.startDynamicPortServer();

    @DynamicPropertySource
    static void gatewayTargets(DynamicPropertyRegistry registry) {
        registry.add("DONNEURS_URL", () -> WireMockBackend.baseUrl(DONNEURS));
        registry.add("DEMANDES_URL", () -> WireMockBackend.baseUrl(DEMANDES));
    }

    @AfterAll
    static void stopWireMock() {
        DONNEURS.stop();
        DEMANDES.stop();
    }

    @LocalServerPort
    private int gatewayPort;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        DONNEURS.resetAll();
        DEMANDES.resetAll();
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
    }

    // ------------------------------------------------------------------
    // Exigence 1 : transfert de chaque route de application.yml
    // ------------------------------------------------------------------

    static Stream<Arguments> routes() {
        return Stream.of(
                Arguments.of("donneurs-centres-creneaux", "/api/v1/centres/5/creneaux", "donneurs"),
                Arguments.of("donneurs-centres", "/api/v1/centres/5", "donneurs"),
                Arguments.of("donneurs-donneurs", "/api/v1/donneurs/42", "donneurs"),
                Arguments.of("donneurs-rendez-vous", "/api/v1/rendez-vous", "donneurs"),
                Arguments.of("donneurs-dons", "/api/v1/dons/3", "donneurs"),
                Arguments.of("donneurs-certificats", "/api/v1/certificats/9", "donneurs"),
                Arguments.of("demandes-demandes", "/api/v1/demandes/1", "demandes"),
                Arguments.of("demandes-appels", "/api/v1/appels", "demandes"),
                Arguments.of("demandes-stock-flux", "/api/v1/stock/niveaux/flux", "demandes"),
                Arguments.of("demandes-stock", "/api/v1/stock/niveaux", "demandes"));
    }

    @ParameterizedTest(name = "{0} : GET {1} est transmis au backend {2}")
    @MethodSource("routes")
    void forwardsEachConfiguredPrefixToItsTargetService(String routeId,
                                                       String path,
                                                       String target) {
        WireMockServer expected = backend(target);
        WireMockServer other = backend("donneurs".equals(target) ? "demandes" : "donneurs");

        stubOk(expected, path);

        client.get()
                .uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("wiremock:" + path);

        expected.verify(getRequestedFor(urlPathEqualTo(path)));
        other.verify(0, anyRequestedFor(anyUrl()));
    }

    // ------------------------------------------------------------------
    // Exigence 2 : preflight CORS
    // ------------------------------------------------------------------

    @Test
    void corsPreflightIsAnsweredWithoutCallingTheBackend() {
        client.options()
                .uri("/api/v1/donneurs/42")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
                .expectHeader().value("Access-Control-Allow-Methods",
                        value -> assertThat(value).contains("GET"))
                .expectHeader().value("Access-Control-Allow-Headers",
                        value -> assertThat(value).containsIgnoringCase("Authorization"));

        DONNEURS.verify(0, anyRequestedFor(anyUrl()));
        DEMANDES.verify(0, anyRequestedFor(anyUrl()));
    }

    // ------------------------------------------------------------------
    // Exigence 8 : X-Request-Id
    // ------------------------------------------------------------------

    @Test
    void providedRequestIdIsForwardedToTheBackend() {
        stubOk(DONNEURS, "/api/v1/donneurs/42");

        client.get()
                .uri("/api/v1/donneurs/42")
                .header("X-Request-Id", "s210-fourni")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "s210-fourni");

        DONNEURS.verify(getRequestedFor(urlPathEqualTo("/api/v1/donneurs/42"))
                .withHeader("X-Request-Id", equalTo("s210-fourni")));
    }

    @Test
    void generatedRequestIdIsReturnedAndForwardedToTheBackend() {
        stubOk(DONNEURS, "/api/v1/donneurs/42");

        EntityExchangeResult<String> result = client.get()
                .uri("/api/v1/donneurs/42")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        String responseRequestId = result.getResponseHeaders().getFirst("X-Request-Id");
        assertThat(responseRequestId).isNotBlank();

        List<LoggedRequest> forwarded = DONNEURS.findAll(
                getRequestedFor(urlPathEqualTo("/api/v1/donneurs/42")));
        assertThat(forwarded).hasSize(1);
        assertThat(forwarded.get(0).getHeader("X-Request-Id"))
                .isEqualTo(responseRequestId);
    }

    // ------------------------------------------------------------------
    // Exigence 7 : 504 quand le backend est lent
    // ------------------------------------------------------------------

    @Test
    void slowWireMockBackendProduces504() {
        DONNEURS.stubFor(get(urlPathEqualTo("/api/v1/donneurs/42"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(2500)
                        .withHeader("Content-Type", "text/plain; charset=UTF-8")
                        .withBody("trop tard")));

        client.get()
                .uri("/api/v1/donneurs/42")
                .exchange()
                .expectStatus().isEqualTo(504)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(504)
                .jsonPath("$.error").isEqualTo("Gateway Timeout");
    }

    // ------------------------------------------------------------------
    // Exigence 9 : streaming SSE, premier evenement avant la fin
    // ------------------------------------------------------------------

    @Test
    void streamedResponseDeliversTheFirstEventBeforeTheEnd() {
        DEMANDES.stubFor(get(urlPathEqualTo("/api/v1/stock/niveaux/flux"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withHeader("Cache-Control", "no-cache")
                        .withBody("data: un\n\ndata: deux\n\n")
                        // Six morceaux etales sur STREAM_DURATION_MS : le
                        // premier evenement complet arrive necessairement
                        // avant la fin de la reponse.
                        .withChunkedDribbleDelay(6, STREAM_DURATION_MS)));

        Flux<String> body = client.get()
                .uri("/api/v1/stock/niveaux/flux")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class)
                .getResponseBody();

        List<String> events = new ArrayList<>();
        List<Long> arrivalMillis = new ArrayList<>();

        long start = System.nanoTime();
        body.doOnNext(event -> {
            events.add(event);
            arrivalMillis.add((System.nanoTime() - start) / 1_000_000L);
        }).blockLast(Duration.ofSeconds(20));
        long endMillis = (System.nanoTime() - start) / 1_000_000L;

        assertThat(arrivalMillis)
                .as("le flux doit emettre au moins un evenement")
                .isNotEmpty();

        // WebTestClient decode les evenements SSE : les prefixes "data: " et
        // les lignes vides de separation ne sont pas conserves dans le corps.
        assertThat(events).hasSizeGreaterThanOrEqualTo(2);
        assertThat(events.get(0)).isEqualTo("un");
        assertThat(String.join("", events))
                .contains("un", "deux");

        // Le flux depasse le timeout global de 800 ms : sa route doit rester
        // ouverte grace a response-timeout=-1.
        assertThat(endMillis)
                .as("le flux doit depasser le timeout global de 800 ms")
                .isGreaterThanOrEqualTo(1500L);

        // Si la reponse entiere etait bufferisee puis livree d'un coup, le
        // premier evenement arriverait au meme instant que la fin du flux :
        // cet ecart minimal prouve une diffusion progressive reelle.
        long firstEventMillis = arrivalMillis.get(0);
        assertThat(firstEventMillis)
                .as("le premier evenement doit arriver nettement avant la fin "
                        + "(premier=%d ms, fin=%d ms)", firstEventMillis, endMillis)
                .isLessThan(endMillis - 500L);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private WireMockServer backend(String target) {
        return "donneurs".equals(target) ? DONNEURS : DEMANDES;
    }

    private void stubOk(WireMockServer server, String path) {
        server.stubFor(get(urlPathEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain; charset=UTF-8")
                        .withBody("wiremock:" + path)));
    }
}
