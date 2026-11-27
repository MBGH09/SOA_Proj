package tn.iit.blood.gateway.security;

/**
 * Corps de la requete {@code POST /api/v1/auth/login} (S2-07).
 */
public record LoginRequest(String username, String password) {
}
