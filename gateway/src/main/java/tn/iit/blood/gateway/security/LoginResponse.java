package tn.iit.blood.gateway.security;

/**
 * Reponse de {@code POST /api/v1/auth/login} (S2-07).
 *
 * <p>Le contrat attendu par le front-end est un objet JSON contenant le champ
 * {@code token} (voir le guide, exemple S2-11).</p>
 */
public record LoginResponse(String token) {
}
