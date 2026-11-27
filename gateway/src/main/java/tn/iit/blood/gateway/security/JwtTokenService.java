package tn.iit.blood.gateway.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Emission des jetons JWT (S2-07).
 *
 * <p>Jeton signe en HS256 avec le secret partage {@code JWT_SECRET}, valable
 * une heure. Les claims sont {@code sub} (nom d'utilisateur), {@code role},
 * {@code iat} et {@code exp}. Le gap 7 n'ayant pas ete accepte, aucun claim
 * {@code donneurId} ou {@code hopitalId} n'est ajoute.</p>
 */
@Service
public class JwtTokenService {

    /** Nom du claim qui porte le role officiel. */
    public static final String ROLE_CLAIM = "role";

    /** Duree de validite exigee par l'enonce : une heure. */
    public static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final JwtEncoder encoder;

    public JwtTokenService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    /** Signe un jeton pour un utilisateur de demonstration. */
    public String issueToken(DemoUser user) {
        Instant issuedAt = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.username())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(TOKEN_TTL))
                .claim(ROLE_CLAIM, user.role())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
