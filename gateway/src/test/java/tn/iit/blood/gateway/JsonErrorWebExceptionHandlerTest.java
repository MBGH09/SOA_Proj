package tn.iit.blood.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import tn.iit.blood.gateway.error.JsonErrorWebExceptionHandler;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires du gestionnaire d'erreurs S2-06.
 *
 * <p>Ils verifient la correspondance des exceptions vers les statuts HTTP et
 * la forme exacte du corps JSON, sans demarrer de serveur.</p>
 */
class JsonErrorWebExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JsonErrorWebExceptionHandler handler =
            new JsonErrorWebExceptionHandler(objectMapper);

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    private JsonNode handle(MockServerWebExchange exchange, Throwable ex) throws Exception {
        handler.handle(exchange, ex).block();

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).isNotBlank();
        return objectMapper.readTree(body);
    }

    @Test
    void connectionRefusedBecomes503WithJsonBody() throws Exception {
        MockServerWebExchange exchange = exchange("/api/v1/donneurs");

        JsonNode body = handle(exchange, new RuntimeException(new ConnectException("Connection refused")));

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(body.get("timestamp").asText()).isNotBlank();
        assertThat(body.get("status").asInt()).isEqualTo(503);
        assertThat(body.get("error").asText()).isEqualTo("Service Unavailable");
        assertThat(body.get("message").asText()).isNotBlank();
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/donneurs");
    }

    @Test
    void responseTimeoutBecomes504WithJsonBody() throws Exception {
        MockServerWebExchange exchange = exchange("/api/v1/demandes");

        JsonNode body = handle(exchange,
                new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Response took longer than timeout"));

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(body.get("status").asInt()).isEqualTo(504);
        assertThat(body.get("error").asText()).isEqualTo("Gateway Timeout");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/demandes");
    }

    @Test
    void bareTimeoutExceptionIsMappedTo504() throws Exception {
        MockServerWebExchange exchange = exchange("/api/v1/demandes");

        JsonNode body = handle(exchange, new TimeoutException("too late"));

        assertThat(body.get("status").asInt()).isEqualTo(504);
        assertThat(body.get("error").asText()).isEqualTo("Gateway Timeout");
    }

    @Test
    void unknownRouteBecomes404WithJsonBody() throws Exception {
        MockServerWebExchange exchange = exchange("/api/v1/route-inconnue");

        JsonNode body = handle(exchange,
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No matching handler"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body.get("status").asInt()).isEqualTo(404);
        assertThat(body.get("error").asText()).isEqualTo("Not Found");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/route-inconnue");
    }

    @Test
    void unexpectedErrorBecomes500WithoutLeakingInternalDetails() throws Exception {
        MockServerWebExchange exchange = exchange("/api/v1/donneurs");

        JsonNode body = handle(exchange, new IllegalStateException("secret interne"));

        assertThat(body.get("status").asInt()).isEqualTo(500);
        assertThat(body.get("error").asText()).isEqualTo("Internal Server Error");
        assertThat(body.get("message").asText())
                .doesNotContain("secret interne")
                .isEqualTo("Erreur interne de la passerelle.");
    }

    @Test
    void committedResponseIsNotRewritten() {
        MockServerWebExchange exchange = exchange("/api/v1/donneurs");
        exchange.getResponse().setComplete().block();

        assertThatThrownBy(() -> handler.handle(exchange, new IllegalStateException("boom")).block())
                .isInstanceOf(IllegalStateException.class);
    }
}
