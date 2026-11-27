package tn.iit.blood.gateway;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tn.iit.blood.gateway.filter.RequestIdFilter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests unitaires du filtre S2-05 : cas d'un en-tete present mais vide,
 * propagation via le Reactor Context, non-fuite du MDC et concurrence.
 */
class RequestIdFilterTest {

    private static final String HEADER = "X-Request-Id";

    @Test
    void blankRequestIdIsReplacedByAGeneratedUuid() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/donneurs").header(HEADER, "   ").build());

        AtomicReference<String> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = mutatedExchange -> {
            forwarded.set(mutatedExchange.getRequest().getHeaders().getFirst(HEADER));
            return Mono.empty();
        };

        new RequestIdFilter().filter(exchange, chain).block();

        String responseId = exchange.getResponse().getHeaders().getFirst(HEADER);

        assertThat(responseId).isNotBlank();
        assertThatCode(() -> UUID.fromString(responseId)).doesNotThrowAnyException();
        assertThat(forwarded.get())
                .as("la requete transmise doit porter l'identifiant genere, pas des espaces")
                .isEqualTo(responseId);
    }

    @Test
    void requestIdIsAvailableThroughReactorContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/donneurs/7")
                        .header(HEADER, "ctx-request-123").build());

        AtomicReference<String> fromContext = new AtomicReference<>();
        GatewayFilterChain chain = mutatedExchange ->
                RequestIdFilter.currentRequestId()
                        .doOnNext(fromContext::set)
                        .then();

        new RequestIdFilter().filter(exchange, chain).block();

        assertThat(fromContext.get())
                .as("la chaine reactive doit lire l'identifiant dans le Reactor Context")
                .isEqualTo("ctx-request-123");
    }

    @Test
    void mdcIsRestoredToItsPreviousValueAfterTheFilter() {
        MDC.put(RequestIdFilter.MDC_KEY, "valeur-preexistante");
        try {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/donneurs")
                            .header(HEADER, "mdc-scope-1").build());
            GatewayFilterChain chain = mutatedExchange -> Mono.empty();

            new RequestIdFilter().filter(exchange, chain).block();

            assertThat(MDC.get(RequestIdFilter.MDC_KEY))
                    .as("le MDC du thread appelant doit etre restaure")
                    .isEqualTo("valeur-preexistante");
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY);
        }

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/donneurs")
                        .header(HEADER, "mdc-scope-2").build());
        new RequestIdFilter().filter(exchange, mutatedExchange -> Mono.empty()).block();

        assertThat(MDC.get(RequestIdFilter.MDC_KEY))
                .as("aucun identifiant ne doit rester dans le MDC du thread appelant")
                .isNull();
    }

    @Test
    void concurrentAsynchronousRequestsKeepTheirOwnRequestId() throws Exception {
        int count = 24;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        ConcurrentHashMap<Integer, String> contextIds = new ConcurrentHashMap<>();
        CopyOnWriteArrayList<String> mdcLeaks = new CopyOnWriteArrayList<>();

        try (MdcLogCapture capture = new MdcLogCapture(RequestIdFilter.class)) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(CompletableFuture.runAsync(() -> {
                    String id = "conc-" + index;
                    MockServerWebExchange exchange = MockServerWebExchange.from(
                            MockServerHttpRequest.get("/api/v1/donneurs/" + index)
                                    .header(HEADER, id).build());

                    GatewayFilterChain chain = mutatedExchange ->
                            Mono.delay(Duration.ofMillis(10L + (index % 5)))
                                    .publishOn(Schedulers.parallel())
                                    .then(RequestIdFilter.currentRequestId()
                                            .doOnNext(found -> contextIds.put(index, found))
                                            .then());

                    new RequestIdFilter().filter(exchange, chain)
                            .block(Duration.ofSeconds(10));

                    String after = MDC.get(RequestIdFilter.MDC_KEY);
                    if (after != null) {
                        mdcLeaks.add(after);
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);

            for (int i = 0; i < count; i++) {
                assertThat(contextIds.get(i))
                        .as("la requete %d doit retrouver son propre identifiant dans le contexte", i)
                        .isEqualTo("conc-" + i);
            }
            assertThat(mdcLeaks).as("le MDC ne doit pas fuir apres chaque requete").isEmpty();

            List<ILoggingEvent> events = capture.events();
            assertThat(events).hasSizeGreaterThanOrEqualTo(count * 2);
            for (ILoggingEvent event : events) {
                String messageId = MdcLogCapture.messageRequestId(event);
                assertThat(messageId).as("les logs du filtre doivent porter un identifiant").isNotNull();
                assertThat(MdcLogCapture.mdcRequestId(event))
                        .as("le MDC du log doit correspondre a l'identifiant du message")
                        .isEqualTo(messageId);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
