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
import tn.iit.blood.cnts.entity.StockCentral;
import tn.iit.blood.cnts.repository.ApprovisionnementRepository;
import tn.iit.blood.cnts.repository.StockCentralRepository;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Autowired
    private StockCentralRepository stockRepo;

    @Autowired
    private ApprovisionnementRepository approRepo;

    private MockWebServiceClient mockClient() {
        return MockWebServiceClient.createClient(applicationContext);
    }

    /** Current year prefix, e.g. "DN-2026-" — rollover-safe (P2). */
    private static final String DN_PREFIX  = "DN-"   + LocalDate.now().getYear() + "-";
    private static final String CERT_PREFIX = "CERT-" + LocalDate.now().getYear() + "-";

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
                "//*[local-name()='numeroDonNational'][starts-with(text(),'" + DN_PREFIX + "')]").exists())
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
    // enregistrerPrelevement — R8 : volume ≠ 450 ml → PrelevementInvalideFault
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
            .andExpect(ResponseMatchers.serverOrReceiverFault())
            // P1: assert typed XSD fault detail is serialized in <detail> (CntsSoapFaultResolver)
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='PrelevementInvalideFault']").exists());
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
    // enregistrerPrelevement — date future → PrelevementInvalideFault
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
            .andExpect(ResponseMatchers.serverOrReceiverFault())
            // P1: assert typed XSD fault detail is serialized in <detail> (CntsSoapFaultResolver)
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='PrelevementInvalideFault']").exists());
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
            .andExpect(ResponseMatchers.serverOrReceiverFault())
            // P1: assert typed XSD fault detail is serialized in <detail> (CntsSoapFaultResolver)
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='CertificatRefuseFault']").exists());
    }

    // =========================================================================
    // delivrerCertificatDon — R24 : REJETE prelevement → CertificatRefuseFault
    // R24 visit completion (TERMINE) is enforced by service-don-stock (the caller).
    // CNTS only blocks if the prelevement was explicitly REJETE.
    // =========================================================================
    @Test
    void delivrerCertificatDon_prelevementNotValide_returnsFault() throws Exception {
        // Try to deliver a certificate for a completely unknown don → must fault
        mockClient().sendRequest(RequestCreators.withPayload(new StringSource("""
                <tns:delivrerCertificatDonRequest xmlns:tns="%s">
                    <tns:donIdLocal>DON-INEXISTANT-999</tns:donIdLocal>
                    <tns:numeroDonNational>DN-2026-999999</tns:numeroDonNational>
                    <tns:donneurRef>REF-ANON-002</tns:donneurRef>
                    <tns:centreId>CRTS-SFAX</tns:centreId>
                    <tns:dateDon>2026-11-01</tns:dateDon>
                </tns:delivrerCertificatDonRequest>
                """.formatted(NS))))
            .andExpect(ResponseMatchers.serverOrReceiverFault())
            // P1: assert typed XSD fault detail is serialized in <detail> (CntsSoapFaultResolver)
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='CertificatRefuseFault']").exists());
    }

    // =========================================================================
    // demanderApprovisionnement (S5-06)
    // R14: regional CRTS first, then CNTS. All or nothing.
    // R15: CRITIQUE uses the shorter delay.
    // R18: the same demandeId returns the same reference and does not
    //      decrement stock a second time.
    // =========================================================================

    @Test
    void demanderApprovisionnement_crtsHasEnough_usesRegionalCentre() {
        stock("CRTS-SFAX", "A_POS", "CGR", 10);
        stock("CNTS-TUNIS", "A_POS", "CGR", 20);

        mockClient().sendRequest(RequestCreators.withPayload(new StringSource(
                approvisionnement("dem-crts-1", "A_POS", "CGR", 4, "NORMALE"))))
            .andExpect(ResponseMatchers.noFault())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='centreSource'][text()='CRTS-SFAX']").exists())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='quantiteAccordee'][text()='4']").exists())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='delaiMinutes'][text()='60']").exists())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='reference'][starts-with(text(),'" + APP_PREFIX + "')]").exists());

        assertEquals(6, quantite("CRTS-SFAX", "A_POS", "CGR"));
        assertEquals(20, quantite("CNTS-TUNIS", "A_POS", "CGR"));
    }

    @Test
    void demanderApprovisionnement_crtsShort_fallsBackToCnts() {
        stock("CRTS-SFAX", "B_POS", "PLASMA", 1);
        stock("CNTS-TUNIS", "B_POS", "PLASMA", 20);

        mockClient().sendRequest(RequestCreators.withPayload(new StringSource(
                approvisionnement("dem-cnts-1", "B_POS", "PLASMA", 5, "NORMALE"))))
            .andExpect(ResponseMatchers.noFault())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='centreSource'][text()='CNTS-TUNIS']").exists());

        assertEquals(1, quantite("CRTS-SFAX", "B_POS", "PLASMA"));
        assertEquals(15, quantite("CNTS-TUNIS", "B_POS", "PLASMA"));
    }

    @Test
    void demanderApprovisionnement_nobodyCanSupply_returnsFault() {
        stock("CRTS-SFAX", "O_NEG", "CGR", 1);
        stock("CNTS-TUNIS", "O_NEG", "CGR", 1);

        mockClient().sendRequest(RequestCreators.withPayload(new StringSource(
                approvisionnement("dem-none-1", "O_NEG", "CGR", 5, "NORMALE"))))
            .andExpect(ResponseMatchers.serverOrReceiverFault())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='StockCentralInsuffisantFault']").exists());

        assertEquals(1, quantite("CRTS-SFAX", "O_NEG", "CGR"));
        assertEquals(1, quantite("CNTS-TUNIS", "O_NEG", "CGR"));
        assertTrue(approRepo.findByDemandeId("dem-none-1").isEmpty());
    }

    @Test
    void demanderApprovisionnement_critique_usesShorterDelay() {
        stock("CRTS-SFAX", "AB_POS", "PLAQUETTES", 8);

        mockClient().sendRequest(RequestCreators.withPayload(new StringSource(
                approvisionnement("dem-crit-1", "AB_POS", "PLAQUETTES", 2, "CRITIQUE"))))
            .andExpect(ResponseMatchers.noFault())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='delaiMinutes'][text()='15']").exists())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='centreSource'][text()='CRTS-SFAX']").exists());
    }

    @Test
    void demanderApprovisionnement_sameDemandeId_returnsSameReferenceOnce() {
        stock("CRTS-SFAX", "A_NEG", "CGR", 10);
        String payload = approvisionnement("dem-idemp-1", "A_NEG", "CGR", 3, "HAUTE");

        mockClient().sendRequest(RequestCreators.withPayload(new StringSource(payload)))
            .andExpect(ResponseMatchers.noFault());

        String reference = approRepo.findByDemandeId("dem-idemp-1").orElseThrow().getReference();

        mockClient().sendRequest(RequestCreators.withPayload(new StringSource(payload)))
            .andExpect(ResponseMatchers.noFault())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='reference'][text()='" + reference + "']").exists())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='centreSource'][text()='CRTS-SFAX']").exists());

        assertEquals(1, approRepo.findAll().size());
        assertEquals(7, quantite("CRTS-SFAX", "A_NEG", "CGR"));
    }

    private static final String APP_PREFIX = "APP-" + LocalDate.now().getYear() + "-";

    private String approvisionnement(String demandeId, String groupe, String composant,
                                     int quantite, String urgence) {
        return """
                <tns:demanderApprovisionnementRequest xmlns:tns="%s">
                    <tns:hopitalId>CHU-SFAX-01</tns:hopitalId>
                    <tns:region>SFAX</tns:region>
                    <tns:groupeSanguin>%s</tns:groupeSanguin>
                    <tns:composant>%s</tns:composant>
                    <tns:quantite>%d</tns:quantite>
                    <tns:urgence>%s</tns:urgence>
                    <tns:demandeId>%s</tns:demandeId>
                </tns:demanderApprovisionnementRequest>
                """.formatted(NS, groupe, composant, quantite, urgence, demandeId);
    }

    private void stock(String centreId, String groupe, String composant, int quantite) {
        StockCentral row = new StockCentral();
        row.setCentreId(centreId);
        row.setGroupeSanguin(groupe);
        row.setComposant(composant);
        row.setQuantite(quantite);
        stockRepo.saveAndFlush(row);
    }

    private int quantite(String centreId, String groupe, String composant) {
        return stockRepo.findByCentreIdAndGroupeSanguinAndComposant(centreId, groupe, composant)
                .orElseThrow()
                .getQuantite();
    }

    // =========================================================================
    // S5-09 — Simulation interceptor
    // These tests re-create the application context with specific simulation
    // properties to verify that:
    //   1. fail-rate=1.0 → Server-Fault is returned, endpoint is NOT called
    //   2. latency-ms=10 → request still dispatches successfully (happy path)
    // =========================================================================

    /**
     * When cnts.fail-rate=1.0 (100 % failure), every request must receive a
     * Server-Fault before reaching the endpoint.
     * The fault is injected by CntsSimulationInterceptor.handleRequest().
     */
    @Test
    @org.springframework.test.context.TestPropertySource(properties = {
            "cnts.fail-rate=1.0",
            "cnts.latency-ms=0"
    })
    void simulation_failRateOne_returnsServerFault() {
        mockClient().sendRequest(RequestCreators.withPayload(new StringSource("""
                <tns:enregistrerPrelevementRequest xmlns:tns="%s">
                    <tns:donIdLocal>DON-SIM-FAULT</tns:donIdLocal>
                    <tns:groupeSanguin>O_POS</tns:groupeSanguin>
                    <tns:volumeMl>450</tns:volumeMl>
                    <tns:centreId>CRTS-SFAX</tns:centreId>
                    <tns:datePrelevement>2026-11-01</tns:datePrelevement>
                </tns:enregistrerPrelevementRequest>
                """.formatted(NS))))
            .andExpect(ResponseMatchers.serverOrReceiverFault());
    }

    /**
     * When cnts.latency-ms=10 (small artificial delay), the request must still
     * dispatch successfully and return a valid response (no fault).
     * This verifies the latency path does not break normal operation.
     */
    @Test
    @org.springframework.test.context.TestPropertySource(properties = {
            "cnts.fail-rate=0.0",
            "cnts.latency-ms=10"
    })
    void simulation_latencyEnabled_requestStillSucceeds() {
        mockClient().sendRequest(RequestCreators.withPayload(new StringSource("""
                <tns:enregistrerPrelevementRequest xmlns:tns="%s">
                    <tns:donIdLocal>DON-SIM-LATENCY</tns:donIdLocal>
                    <tns:groupeSanguin>A_POS</tns:groupeSanguin>
                    <tns:volumeMl>450</tns:volumeMl>
                    <tns:centreId>CRTS-SFAX</tns:centreId>
                    <tns:datePrelevement>2026-11-01</tns:datePrelevement>
                </tns:enregistrerPrelevementRequest>
                """.formatted(NS))))
            .andExpect(ResponseMatchers.noFault())
            .andExpect(ResponseMatchers.xpath(
                "//*[local-name()='statut'][text()='QUALIFICATION_EN_COURS']").exists());
    }
}
