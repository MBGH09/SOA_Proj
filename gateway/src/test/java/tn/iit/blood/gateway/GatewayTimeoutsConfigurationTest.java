package tn.iit.blood.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de configuration des timeouts S2-04.
 *
 * <p>Les variables d'environnement sont simulees via l'attribut {@code properties}
 * de {@link SpringBootTest} : cela prouve que les delais sont configurables sans
 * modifier le code. Le timeout global de reponse doit etre desactive uniquement
 * pour la route du flux de stock, via les metadonnees de route.</p>
 */
@SpringBootTest(properties = {
        "GATEWAY_CONNECT_TIMEOUT_MS=1234",
        "GATEWAY_RESPONSE_TIMEOUT_MS=4321"
})
class GatewayTimeoutsConfigurationTest {

    @Autowired
    private HttpClientProperties httpClientProperties;

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    private Map<String, RouteDefinition> routesById() {
        List<RouteDefinition> definitions = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block();
        assertThat(definitions).isNotNull();
        return definitions.stream().collect(Collectors.toMap(RouteDefinition::getId, definition -> definition));
    }

    @Test
    void connectAndResponseTimeoutsComeFromEnvironmentVariables() {
        assertThat(httpClientProperties.getConnectTimeout())
                .as("connect-timeout doit venir de GATEWAY_CONNECT_TIMEOUT_MS")
                .isEqualTo(1234);
        assertThat(httpClientProperties.getResponseTimeout())
                .as("response-timeout doit venir de GATEWAY_RESPONSE_TIMEOUT_MS")
                .isEqualTo(Duration.ofMillis(4321));
    }

    @Test
    void streamRouteDisablesTheResponseTimeout() {
        RouteDefinition flux = routesById().get("demandes-stock-flux");

        assertThat(flux).as("route demandes-stock-flux").isNotNull();
        assertThat(flux.getMetadata()).containsKey("response-timeout");
        assertThat(((Number) flux.getMetadata().get("response-timeout")).longValue())
                .as("un response-timeout negatif desactive le timeout")
                .isEqualTo(-1L);
    }

    @Test
    void streamRouteTargetsOnlyTheSsePath() {
        RouteDefinition flux = routesById().get("demandes-stock-flux");

        assertThat(flux.getPredicates()).isNotEmpty();
        assertThat(flux.getPredicates().get(0).getName()).isEqualTo("Path");
        assertThat(flux.getPredicates().get(0).getArgs().values())
                .containsExactly("/api/v1/stock/niveaux/flux");
    }

    @Test
    void otherRoutesKeepTheGlobalResponseTimeout() {
        Map<String, RouteDefinition> routes = routesById();

        for (String id : List.of("donneurs-donneurs", "demandes-demandes", "demandes-stock")) {
            assertThat(routes.get(id).getMetadata())
                    .as("la route %s ne doit pas desactiver le timeout global", id)
                    .doesNotContainKey("response-timeout");
        }
    }
}
