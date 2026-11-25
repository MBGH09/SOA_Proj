package tn.iit.blood.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import tn.iit.blood.gateway.security.DemoUser;
import tn.iit.blood.gateway.security.JwtTokenService;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de l'emission JWT (S2-07), sans demarrer de serveur.
 */
class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-jwt-secret-0123456789-abcdef";

    private final SecretKey key = new SecretKeySpec(
            SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

    private final JwtTokenService service = new JwtTokenService(encoder);

    @Test
    void tokenIsSignedInHs256WithOfficialClaimsAndOneHourTtl() {
        DemoUser donor = new DemoUser("donor", "donor", "secret", "DONOR");

        String token = service.issueToken(donor);
        Jwt jwt = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build()
                .decode(token);

        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(jwt.getSubject()).isEqualTo("donor");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("DONOR");
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofHours(1));
    }

    @Test
    void tokenContainsNoGap7ClaimsWhenGap7IsDeferred() {
        DemoUser donor = new DemoUser("donor", "donor", "secret", "DONOR");

        Jwt jwt = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build()
                .decode(service.issueToken(donor));

        assertThat(jwt.getClaimAsString("donneurId")).isNull();
        assertThat(jwt.getClaimAsString("hopitalId")).isNull();
    }
}
