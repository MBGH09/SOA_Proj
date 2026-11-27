package tn.iit.blood.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de configuration des routes S2-02.
 *
 * <p>Les variables d'environnement sont simulees via l'attribut {@code properties}
 * de {@link SpringBootTest} : cela prouve que les destinations sont bien
 * configurables et surchargeables sans modifier le code.</p>
 */
@SpringBootTest(properties = {
        "DONNEURS_URL=http://donneurs.test:19081",
        "DEMANDES_URL=http://demandes.test:19082"
})
class GatewayRoutesConfigurationTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    private Map<String, RouteDefinition> routesById() {
        List<RouteDefinition> definitions = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block();
        assertThat(definitions).isNotNull();
        return definitions.stream().collect(Collectors.toMap(
                RouteDefinition::getId,
                definition -> definition,
                (first, second) -> first,
                LinkedHashMap::new));
    }

    @Test
    void allExpectedRoutesAreDefined() {
        Map<String, RouteDefinition> routes = routesById();

        assertThat(routes).containsKeys(
                "donneurs-donneurs",
                "donneurs-centres",
                "donneurs-rendez-vous",
                "donneurs-dons",
                "donneurs-certificats",
                "donneurs-centres-creneaux",
                "demandes-demandes",
                "demandes-appels",
                "demandes-stock-flux",
                "demandes-stock");
        assertThat(routes).hasSize(10);
    }

    @Test
    void donneursRoutesTargetTheConfiguredUrl() {
        Map<String, RouteDefinition> routes = routesById();

        for (String id : List.of(
                "donneurs-donneurs",
                "donneurs-centres",
                "donneurs-rendez-vous",
                "donneurs-dons",
                "donneurs-certificats",
                "donneurs-centres-creneaux")) {
            assertThat(routes.get(id).getUri())
                    .as("cible de la route %s", id)
                    .hasToString("http://donneurs.test:19081");
        }
    }

    @Test
    void demandesRoutesTargetTheConfiguredUrl() {
        Map<String, RouteDefinition> routes = routesById();

        for (String id : List.of("demandes-demandes", "demandes-appels", "demandes-stock")) {
            assertThat(routes.get(id).getUri())
                    .as("cible de la route %s", id)
                    .hasToString("http://demandes.test:19082");
        }
    }

    @Test
    void pathPredicatesMatchTheExpectedPatterns() {
        Map<String, RouteDefinition> routes = routesById();

        assertPath(routes, "donneurs-donneurs", "/api/v1/donneurs/**");
        assertPath(routes, "donneurs-centres", "/api/v1/centres/**");
        assertPath(routes, "donneurs-rendez-vous", "/api/v1/rendez-vous/**");
        assertPath(routes, "donneurs-dons", "/api/v1/dons/**");
        assertPath(routes, "donneurs-certificats", "/api/v1/certificats/**");
        assertPath(routes, "donneurs-centres-creneaux", "/api/v1/centres/{id}/creneaux");
        assertPath(routes, "demandes-demandes", "/api/v1/demandes/**");
        assertPath(routes, "demandes-appels", "/api/v1/appels/**");
        assertPath(routes, "demandes-stock", "/api/v1/stock/**");
    }

    @Test
    void creneauxRouteHasPriorityOverTheGeneralCentresRoute() {
        Map<String, RouteDefinition> routes = routesById();

        int creneauxOrder = routes.get("donneurs-centres-creneaux").getOrder();
        int centresOrder = routes.get("donneurs-centres").getOrder();

        assertThat(creneauxOrder)
                .as("la route creneaux doit etre evaluee avant la route centres")
                .isLessThan(centresOrder);
    }

    private void assertPath(Map<String, RouteDefinition> routes, String routeId, String expectedPath) {
        RouteDefinition route = routes.get(routeId);
        assertThat(route).as("route %s", routeId).isNotNull();

        boolean found = route.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .flatMap(predicate -> predicate.getArgs().values().stream())
                .anyMatch(expectedPath::equals);

        assertThat(found)
                .as("le predicat Path de %s doit contenir %s", routeId, expectedPath)
                .isTrue();
    }
}
