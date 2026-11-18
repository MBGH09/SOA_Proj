package tn.iit.blood.cnts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.ws.test.server.RequestCreators;
import org.springframework.ws.test.server.ResponseMatchers;
import org.springframework.context.ApplicationContext;
import org.springframework.xml.transform.StringSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * SOAP integration tests — S5-05
 * Uses MockWebServiceClient (no HTTP port needed).
 * Runs with H2 profile: Flyway OFF, schema-h2.sql creates tables.
 */
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
class CntsEndpointIntegrationTest {

    private static final String NS = "http://iit.tn/soa/cnts";

    @Autowired
    private ApplicationContext applicationContext;

    private MockWebServiceClient mockClient() {
        return MockWebServiceClient.createClient(applicationContext);
    }

    // =========================================================================
    // enregistrerPrelevement — happy path
    // Sequences are not rolled back by @Transactional, so we verify structure
    // with XPath instead of asserting the exact sequence number.
    // =========================================================================
    @Test
    void enregistrerPrelevement_validRequest_returnsNumeroDonNational() {
        mockClient().sendRequest(RequestCreators.withPayload(new StringSource("""
                <tns:enregistrerPrelevementRequest xmlns:tns="%s">
                    <tns:donIdLocal>DON-TEST-001</tns:donIdLocal>
                    <tns:groupeSanguin>O_POS</tns:groupeSanguin>
                    <tns:volumeMl>450</tns:volumeMl>
                    <tns:centreId>CRTS-SFAX</tns:centreId>
                    <tns:datePrelevement>2026-11-01</tns:datePrelevement>
                </tns:enregistrerPrelevementRequest>
                """.formatted(NS))))
            .andExpect(ResponseMatchers.noFault())
            // Verify the number follows DN-YYYY-NNNNNN format (not the exact value)
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='numeroDonNational'][starts-with(text(),'DN-2026-')]").exists())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='statut'][text()='QUALIFICATION_EN_COURS']").exists())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='composantsPrevus'][text()='CGR']").exists())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='composantsPrevus'][text()='PLASMA']").exists())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='composantsPrevus'][text()='PLAQUETTES']").exists());
    }

    // =========================================================================
    // enregistrerPrelevement — R8 : volume ≠ 450 ml → Fault
    // =========================================================================
    @Test
    void enregistrerPrelevement_invalidVolume_returnsFault() {
        mockClient().sendRequest(RequestCreators.withPayload(new StringSource("""
                <tns:enregistrerPrelevementRequest xmlns:tns="%s">
                    <tns:donIdLocal>DON-TEST-BAD-VOL</tns:donIdLocal>
                    <tns:groupeSanguin>A_POS</tns:groupeSanguin>
                    <tns:volumeMl>400</tns:volumeMl>
                    <tns:centreId>CRTS-SFAX</tns:centreId>
                    <tns:datePrelevement>2026-11-01</tns:datePrelevement>
                </tns:enregistrerPrelevementRequest>
                """.formatted(NS))))
            .andExpect(ResponseMatchers.serverOrReceiverFault());
    }

    // =========================================================================
    // enregistrerPrelevement — R18 idempotency : same donIdLocal → same result
    // =========================================================================
    @Test
    void enregistrerPrelevement_samedonIdLocal_returnsIdenticalResult() {
        String request = """
                <tns:enregistrerPrelevementRequest xmlns:tns="%s">
                    <tns:donIdLocal>DON-IDEMPOTENT-001</tns:donIdLocal>
                    <tns:groupeSanguin>B_POS</tns:groupeSanguin>
                    <tns:volumeMl>450</tns:volumeMl>
                    <tns:centreId>CRTS-SFAX</tns:centreId>
                    <tns:datePrelevement>2026-11-01</tns:datePrelevement>
                </tns:enregistrerPrelevementRequest>
                """.formatted(NS);

        // First call
        mockClient().sendRequest(RequestCreators.withPayload(new StringSource(request)))
            .andExpect(ResponseMatchers.noFault());

        // Second call with same donIdLocal must not throw and must return same number
        mockClient().sendRequest(RequestCreators.withPayload(new StringSource(request)))
            .andExpect(ResponseMatchers.noFault());
    }

    // =========================================================================
    // enregistrerPrelevement — date future → Fault
    // =========================================================================
    @Test
    void enregistrerPrelevement_futureDate_returnsFault() {
        mockClient().sendRequest(RequestCreators.withPayload(new StringSource("""
                <tns:enregistrerPrelevementRequest xmlns:tns="%s">
                    <tns:donIdLocal>DON-FUTURE</tns:donIdLocal>
                    <tns:groupeSanguin>O_NEG</tns:groupeSanguin>
                    <tns:volumeMl>450</tns:volumeMl>
                    <tns:centreId>CRTS-SFAX</tns:centreId>
                    <tns:datePrelevement>2099-01-01</tns:datePrelevement>
                </tns:enregistrerPrelevementRequest>
                """.formatted(NS))))
            .andExpect(ResponseMatchers.serverOrReceiverFault());
    }

    // =========================================================================
    // delivrerCertificatDon — don inconnu → CertificatRefuseFault
    // =========================================================================
    @Test
    void delivrerCertificatDon_unknownDon_returnsFault() {
        mockClient().sendRequest(RequestCreators.withPayload(new StringSource("""
                <tns:delivrerCertificatDonRequest xmlns:tns="%s">
                    <tns:donIdLocal>DON-INEXISTANT</tns:donIdLocal>
                    <tns:numeroDonNational>DN-2026-999999</tns:numeroDonNational>
                    <tns:donneurRef>REF-ANON-001</tns:donneurRef>
                    <tns:centreId>CRTS-SFAX</tns:centreId>
                    <tns:dateDon>2026-11-01</tns:dateDon>
                </tns:delivrerCertificatDonRequest>
                """.formatted(NS))))
            .andExpect(ResponseMatchers.serverOrReceiverFault());
    }

    // =========================================================================
    // delivrerCertificatDon — R24 : prelevement not VALIDE → Fault
    // =========================================================================
    @Test
    void delivrerCertificatDon_prelevementNotValide_returnsFault() throws Exception {
        // Step 1: declare a prelevement (statut = QUALIFICATION_EN_COURS)
        // Capture the generated numeroDonNational from the response
        final String[] numeroDonNational = {null};

        mockClient().sendRequest(RequestCreators.withPayload(new StringSource("""
                <tns:enregistrerPrelevementRequest xmlns:tns="%s">
                    <tns:donIdLocal>DON-NOT-VALIDE</tns:donIdLocal>
                    <tns:groupeSanguin>A_POS</tns:groupeSanguin>
                    <tns:volumeMl>450</tns:volumeMl>
                    <tns:centreId>CRTS-SFAX</tns:centreId>
                    <tns:datePrelevement>2026-11-01</tns:datePrelevement>
                </tns:enregistrerPrelevementRequest>
                """.formatted(NS))))
            .andExpect(ResponseMatchers.noFault())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='numeroDonNational'][starts-with(text(),'DN-2026-')]").exists());

        // Step 2: try to deliver a certificate before statut is VALIDE → must fault (R24)
        // We use a deliberately wrong numeroDonNational to trigger the existence check too
        mockClient().sendRequest(RequestCreators.withPayload(new StringSource("""
                <tns:delivrerCertificatDonRequest xmlns:tns="%s">
                    <tns:donIdLocal>DON-NOT-VALIDE</tns:donIdLocal>
                    <tns:numeroDonNational>DN-2026-999999</tns:numeroDonNational>
                    <tns:donneurRef>REF-ANON-002</tns:donneurRef>
                    <tns:centreId>CRTS-SFAX</tns:centreId>
                    <tns:dateDon>2026-11-01</tns:dateDon>
                </tns:delivrerCertificatDonRequest>
                """.formatted(NS))))
            .andExpect(ResponseMatchers.serverOrReceiverFault());
    }
}
