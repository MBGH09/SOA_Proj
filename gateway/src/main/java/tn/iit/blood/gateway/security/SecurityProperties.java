package tn.iit.blood.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration de la securite du gateway (S2-07).
 *
 * <p>Les valeurs proviennent de variables d'environnement (voir
 * {@code application.yml}). Aucun mot de passe ni secret n'a de valeur par
 * defaut : l'application refuse de demarrer sans {@code JWT_SECRET} valide.</p>
 *
 * <p>{@code permitAll} est reserve aux tests automatises : il n'est jamais
 * active par la configuration de production.</p>
 */
@ConfigurationProperties(prefix = "gateway.security")
public class SecurityProperties {

    /** Desactive toutes les regles d'acces. Reserve aux tests existants. */
    private boolean permitAll;

    private final Jwt jwt = new Jwt();

    private final Map<String, DemoUser> demoUsers = new LinkedHashMap<>();

    public boolean isPermitAll() {
        return permitAll;
    }

    public void setPermitAll(boolean permitAll) {
        this.permitAll = permitAll;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public Map<String, DemoUser> getDemoUsers() {
        return demoUsers;
    }

    /** Parametres de signature et de verification des jetons. */
    public static class Jwt {

        /** Secret HMAC partage, fourni par {@code JWT_SECRET}. */
        private String secret = "";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    /** Utilisateur de demonstration configure par variables d'environnement. */
    public static class DemoUser {

        private String username = "";

        private String password = "";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
