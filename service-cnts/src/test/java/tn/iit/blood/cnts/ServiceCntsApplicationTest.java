package tn.iit.blood.cnts;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("h2")   // avoids PostgreSQL connection in CI without Docker
class ServiceCntsApplicationTest {

    @Test
    void contextLoads() {
        // Verifies the Spring context starts without errors on H2 (no Docker needed)
    }
}

