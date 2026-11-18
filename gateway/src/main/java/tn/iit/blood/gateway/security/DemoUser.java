package tn.iit.blood.gateway.security;

/**
 * Utilisateur de demonstration (S2-07).
 *
 * @param key      cle de configuration ({@code donor}, {@code staff},
 *                 {@code hopital}, {@code admin})
 * @param username identifiant envoye dans la requete de connexion
 * @param password mot de passe fourni par variable d'environnement
 * @param role     role officiel ({@code DONOR}, {@code STAFF_CENTRE},
 *                 {@code HOPITAL}, {@code ADMIN})
 */
public record DemoUser(String key, String username, String password, String role) {
}
