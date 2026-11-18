package tn.iit.blood.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import tn.iit.blood.gateway.filter.RequestIdFilter;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Test unitaire du filtre S2-05 pour le cas d'un en-tete present mais vide,
 * qui ne peut pas etre envoye via un vrai client HTTP (valeur invalide).
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
}
