package tn.iit.blood.gateway;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tn.iit.blood.gateway.filter.RequestIdFilter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests du filtre d'identification des requetes S2-05.
 *
 * <p>Un serveur HTTP simule (JDK {@link HttpServer}) joue le role du microservice
 * et memorise l'en-tete {@code X-Request-Id} qu'il recoit. On verifie ainsi que
 * l'identifiant genere (ou fourni) revient au client, qu'il est transmis au
 * backend et qu'il apparait dans les logs. Il ne s'agit pas d'un test contre les
 * vrais microservices.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class RequestIdFilterIntegrationTest {

    private static final String HEADER = "X-Request-Id";

    private static final AtomicReference<String> RECEIVED_REQUEST_ID = new AtomicReference<>();

    private static final HttpServer DOWNSTREAM;

    static {
        try {
            DOWNSTREAM = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            DOWNSTREAM.createContext("/", exchange -> {
                String requestId = exchange.getRequestHeaders().getFirst(HEADER);
                RECEIVED_REQUEST_ID.set(requestId);
                // Le corps renvoie l'identifiant recu : chaque requete concurrente
                // peut ainsi verifier qu'aucun identifiant n'a ete melange.
                byte[] body = (requestId == null ? "ok" : requestId)
                        .getBytes(StandardCharsets.UTF_8);
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
        RECEIVED_REQUEST_ID.set(null);
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
    }

    @Test
    void generatedRequestIdIsReturnedForwardedAndLogged(CapturedOutput output) {
        EntityExchangeResult<String> result = client.get()
                .uri("/api/v1/donneurs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        String requestId = result.getResponseHeaders().getFirst(HEADER);

        assertThat(requestId).as("un X-Request-Id doit etre renvoye au client").isNotBlank();
        assertThatCode(() -> UUID.fromString(requestId))
                .as("l'identifiant genere doit etre un UUID")
                .doesNotThrowAnyException();
        assertThat(RECEIVED_REQUEST_ID.get())
                .as("le backend doit recevoir le meme identifiant")
                .isEqualTo(requestId);
        assertThat(output.getOut())
                .as("l'identifiant doit apparaitre dans les logs")
                .contains(requestId);
    }

    @Test
    void providedRequestIdIsPreservedForwardedAndLogged(CapturedOutput output) {
        EntityExchangeResult<String> result = client.get()
                .uri("/api/v1/donneurs")
                .header(HEADER, "test-request-id-123")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        assertThat(result.getResponseHeaders().getFirst(HEADER)).isEqualTo("test-request-id-123");
        assertThat(RECEIVED_REQUEST_ID.get()).isEqualTo("test-request-id-123");
        assertThat(output.getOut()).contains("test-request-id-123");
    }

    @Test
    void concurrentRequestsKeepTheirOwnRequestIdInHeadersBackendAndLogs() {
        int count = 16;
        List<String> expectedIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            expectedIds.add("http-conc-" + i);
        }

        try (MdcLogCapture capture = new MdcLogCapture(RequestIdFilter.class)) {
            List<String> responseIds = Flux.range(0, count)
                    .flatMap(index -> Mono.fromCallable(() -> {
                        String id = "http-conc-" + index;
                        EntityExchangeResult<String> result = client.get()
                                .uri("/api/v1/donneurs/" + index)
                                .header(HEADER, id)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(String.class)
                                .returnResult();

                        assertThat(result.getResponseBody())
                                .as("le backend doit recevoir l'identifiant de cette requete")
                                .isEqualTo(id);
                        assertThat(result.getResponseHeaders().getFirst(HEADER))
                                .as("la reponse doit porter le meme identifiant")
                                .isEqualTo(id);
                        return id;
                    }).subscribeOn(Schedulers.boundedElastic()))
                    .collectList()
                    .block(Duration.ofSeconds(30));

            assertThat(responseIds).containsExactlyInAnyOrderElementsOf(expectedIds);

            List<ILoggingEvent> events = capture.events();
            assertThat(events).hasSizeGreaterThanOrEqualTo(count * 2);
            for (ILoggingEvent event : events) {
                String messageId = MdcLogCapture.messageRequestId(event);
                assertThat(messageId).as("les logs du filtre doivent porter un identifiant").isNotNull();
                assertThat(MdcLogCapture.mdcRequestId(event))
                        .as("le MDC du log doit correspondre a l'identifiant du message")
                        .isEqualTo(messageId);
                assertThat(expectedIds).contains(messageId);
            }
        }
    }

    @Test
    void healthEndpointIsExposed() {
        client.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}
