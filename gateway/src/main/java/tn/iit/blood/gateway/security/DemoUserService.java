package tn.iit.blood.gateway.security;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Charge les quatre utilisateurs de demonstration depuis la configuration
 * (S2-07).
 *
 * <p>Les roles sont fixes par l'enonce : {@code donor} -&gt; {@code DONOR},
 * {@code staff} -&gt; {@code STAFF_CENTRE}, {@code hopital} -&gt; {@code HOPITAL},
 * {@code admin} -&gt; {@code ADMIN}. Un utilisateur dont le nom ou le mot de passe
 * est absent provoque un echec de demarrage : la configuration de demonstration
 * doit etre explicite.</p>
 */
@Component
public class DemoUserService {

    /** Ordre stable utilise dans les messages et les tests. */
    private static final List<String> USER_KEYS = List.of("donor", "staff", "hopital", "admin");

    private static final Map<String, String> ROLES = roles();

    private final List<DemoUser> users;

    public DemoUserService(SecurityProperties properties) {
        List<DemoUser> configured = new ArrayList<>();
        Set<String> usernames = new HashSet<>();

        for (String key : USER_KEYS) {
            SecurityProperties.DemoUser user = properties.getDemoUsers().get(key);
            if (user == null
                    || !StringUtils.hasText(user.getUsername())
                    || !StringUtils.hasText(user.getPassword())) {
                throw new IllegalStateException(
                        "Utilisateur de demonstration incomplet : " + key
                                + ". Definir " + envPrefix(key) + "_USERNAME et "
                                + envPrefix(key) + "_PASSWORD.");
            }

            String username = user.getUsername().trim();
            if (!usernames.add(username)) {
                throw new IllegalStateException(
                        "Nom d'utilisateur de demonstration en double : " + username);
            }

            configured.add(new DemoUser(key, username, user.getPassword(), ROLES.get(key)));
        }

        this.users = List.copyOf(configured);
    }

    private static Map<String, String> roles() {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put("donor", UserRoles.DONOR);
        roles.put("staff", UserRoles.STAFF_CENTRE);
        roles.put("hopital", UserRoles.HOPITAL);
        roles.put("admin", UserRoles.ADMIN);
        return Map.copyOf(roles);
    }

    private static String envPrefix(String key) {
        return "DEMO_" + key.toUpperCase();
    }

    /** Tous les utilisateurs configures, dans l'ordre officiel. */
    public List<DemoUser> getUsers() {
        return users;
    }

    /** Recherche un utilisateur par son identifiant exact (deja trimme). */
    public Optional<DemoUser> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return users.stream()
                .filter(user -> user.username().equals(username))
                .findFirst();
    }
}
