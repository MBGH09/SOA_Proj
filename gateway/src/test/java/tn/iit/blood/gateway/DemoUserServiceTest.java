package tn.iit.blood.gateway;

import org.junit.jupiter.api.Test;
import tn.iit.blood.gateway.security.DemoUser;
import tn.iit.blood.gateway.security.DemoUserService;
import tn.iit.blood.gateway.security.SecurityProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires du chargement des utilisateurs de demonstration (S2-07).
 */
class DemoUserServiceTest {

    @Test
    void fourUsersAreLoadedWithTheirOfficialRoles() {
        DemoUserService service = new DemoUserService(properties());

        List<DemoUser> users = service.getUsers();

        assertThat(users).extracting(DemoUser::key)
                .containsExactly("donor", "staff", "hopital", "admin");
        assertThat(users).extracting(DemoUser::role)
                .containsExactly("DONOR", "STAFF_CENTRE", "HOPITAL", "ADMIN");
        assertThat(service.findByUsername("staff"))
                .get()
                .extracting(DemoUser::role)
                .isEqualTo("STAFF_CENTRE");
    }

    @Test
    void unknownUsernameIsRejected() {
        DemoUserService service = new DemoUserService(properties());

        assertThat(service.findByUsername("inconnu")).isEmpty();
    }

    @Test
    void duplicateUsernameStopsTheStartup() {
        SecurityProperties properties = properties();
        properties.getDemoUsers().get("staff").setUsername("donor");

        assertThatThrownBy(() -> new DemoUserService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("en double");
    }

    @Test
    void missingPasswordStopsTheStartup() {
        SecurityProperties properties = properties();
        properties.getDemoUsers().get("admin").setPassword("");

        assertThatThrownBy(() -> new DemoUserService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_ADMIN_PASSWORD");
    }

    @Test
    void missingUserStopsTheStartup() {
        SecurityProperties properties = properties();
        properties.getDemoUsers().remove("hopital");

        assertThatThrownBy(() -> new DemoUserService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hopital");
    }

    private static SecurityProperties properties() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret("unit-test-jwt-secret-0123456789-abcdef");
        add(properties, "donor", "donor", "donor-password");
        add(properties, "staff", "staff", "staff-password");
        add(properties, "hopital", "hopital", "hopital-password");
        add(properties, "admin", "admin", "admin-password");
        return properties;
    }

    private static void add(SecurityProperties properties,
                            String key,
                            String username,
                            String password) {
        SecurityProperties.DemoUser user = new SecurityProperties.DemoUser();
        user.setUsername(username);
        user.setPassword(password);
        properties.getDemoUsers().put(key, user);
    }
}
