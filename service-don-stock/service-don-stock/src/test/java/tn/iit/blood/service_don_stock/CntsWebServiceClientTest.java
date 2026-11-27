package tn.iit.blood.service_don_stock;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.util.JAXBSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import tn.iit.blood.donstock.soap.client.DelivrerCertificatDonResponse;
import tn.iit.blood.donstock.soap.client.DemanderApprovisionnementResponse;
import tn.iit.blood.donstock.soap.client.EnregistrerPrelevementResponse;
import tn.iit.blood.service_don_stock.soap.CertificatRefuseException;
import tn.iit.blood.service_don_stock.soap.CntsWebServiceClient;
import tn.iit.blood.service_don_stock.soap.PrelevementInvalideException;
import tn.iit.blood.service_don_stock.soap.StockInsuffisantException;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.anything;
import static org.springframework.ws.test.client.RequestMatchers.xpath;
import static org.springframework.ws.test.client.ResponseCreators.withException;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;
import static org.springframework.ws.test.client.ResponseCreators.withSoapEnvelope;

/**
 * S4-13 — SOAP client tests with MockWebServiceServer.
 *
 * Covers the three operations (success), the three CNTS faults mapped to
 * domain exceptions, and a transport timeout.
 */
class CntsWebServiceClientTest {

    private static final Map<String, String> NS = Map.of("cnts", "http://iit.tn/soa/cnts");

    private Jaxb2Marshaller marshaller;
    private MockWebServiceServer server;
    private CntsWebServiceClient client;

    @BeforeEach
    void setUp() throws Exception {
        marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("tn.iit.blood.donstock.soap.client");
        marshaller.afterPropertiesSet();

        WebServiceTemplate template = new WebServiceTemplate(marshaller);
        server = MockWebServiceServer.createServer(template);
        client = new CntsWebServiceClient(template);
        ReflectionTestUtils.setField(client, "cntsUrl", "http://localhost/ws");
    }

    @AfterEach
    void verifyAllRequestsWereConsumed() {
        server.verify();
    }

    // ================================================================
    // Success paths
    // ================================================================

    @Test
    void enregistrerPrelevement_success_sendsTheCollectionDate() throws Exception {
        EnregistrerPrelevementResponse resp = new EnregistrerPrelevementResponse();
        resp.setNumeroDonNational("DN-2026-9001");
        resp.setStatut("QUALIFICATION_EN_COURS");

        server.expect(xpath("//cnts:datePrelevement", NS).evaluatesTo("2026-11-20"))
                .andRespond(withPayload(payload(resp)));

        EnregistrerPrelevementResponse result = client.enregistrerPrelevement(
                "don-1", "A_POS", 450, "lieu-crts-sfax", LocalDate.of(2026, 11, 20));

        assertThat(result.getNumeroDonNational()).isEqualTo("DN-2026-9001");
    }

    @Test
    void demanderApprovisionnement_success_sendsRegionAndDemandeId() throws Exception {
        DemanderApprovisionnementResponse resp = new DemanderApprovisionnementResponse();
        resp.setReference("REF-9002");
        resp.setCentreSource("CRTS-SFAX");
        resp.setQuantiteAccordee(3);
        resp.setDelaiMinutes(45);
        resp.setStatut("ACCORDE");

        server.expect(xpath("//cnts:region", NS).evaluatesTo("Sfax"))
                .andExpect(xpath("//cnts:demandeId", NS).evaluatesTo("demande-1"))
                .andRespond(withPayload(payload(resp)));

        DemanderApprovisionnementResponse result = client.demanderApprovisionnement(
                "lieu-chu-hbs", "Sfax", "A_POS", "CGR", 3, "HAUTE", "demande-1");

        assertThat(result.getReference()).isEqualTo("REF-9002");
        assertThat(result.getCentreSource()).isEqualTo("CRTS-SFAX");
        assertThat(result.getDelaiMinutes()).isEqualTo(45);
    }

    @Test
    void delivrerCertificatDon_success_sendsTheDonationDate() throws Exception {
        DelivrerCertificatDonResponse resp = new DelivrerCertificatDonResponse();
        resp.setNumeroCertificat("CERT-9003");
        resp.setDateEmission(DatatypeFactory.newInstance().newXMLGregorianCalendar("2026-11-23"));
        resp.setStatut("VALIDE");

        server.expect(xpath("//cnts:dateDon", NS).evaluatesTo("2026-11-21"))
                .andRespond(withPayload(payload(resp)));

        DelivrerCertificatDonResponse result = client.delivrerCertificatDon(
                "don-1", "DN-2026-9001", "donneur-1", "lieu-crts-sfax",
                LocalDate.of(2026, 11, 21));

        assertThat(result.getNumeroCertificat()).isEqualTo("CERT-9003");
        assertThat(result.getDateEmission().getDay()).isEqualTo(23);
    }

    // ================================================================
    // Faults — mapped to domain exceptions (never retried)
    // ================================================================

    @Test
    void prelevementInvalideFault_mapsToDomainException() {
        server.expect(anything())
                .andRespond(withSoapEnvelope(fault("PrelevementInvalideFault", "invalid pouch")));

        assertThatThrownBy(() -> client.enregistrerPrelevement(
                "don-2", "A_POS", 300, "lieu-crts-sfax", LocalDate.of(2026, 11, 20)))
                .isInstanceOf(PrelevementInvalideException.class)
                .hasMessageContaining("invalid pouch");
    }

    @Test
    void stockCentralInsuffisantFault_mapsToDomainException() {
        server.expect(anything())
                .andRespond(withSoapEnvelope(fault("StockCentralInsuffisantFault", "no stock")));

        assertThatThrownBy(() -> client.demanderApprovisionnement(
                "lieu-chu-hbs", "Sfax", "A_POS", "CGR", 99, "CRITIQUE", "demande-2"))
                .isInstanceOf(StockInsuffisantException.class)
                .hasMessageContaining("no stock");
    }

    @Test
    void certificatRefuseFault_mapsToDomainException() {
        server.expect(anything())
                .andRespond(withSoapEnvelope(fault("CertificatRefuseFault", "donor not eligible")));

        assertThatThrownBy(() -> client.delivrerCertificatDon(
                "don-3", "DN-3", "donneur-3", "lieu-crts-sfax", LocalDate.of(2026, 11, 21)))
                .isInstanceOf(CertificatRefuseException.class)
                .hasMessageContaining("donor not eligible");
    }

    // ================================================================
    // Timeout — technical error, retryable by Resilience4j
    // ================================================================

    @Test
    void timeout_propagatesTransportException() {
        server.expect(anything())
                .andRespond(withException(new SocketTimeoutException("simulated SOAP timeout")));

        assertThatThrownBy(() -> client.demanderApprovisionnement(
                "lieu-chu-hbs", "Sfax", "A_POS", "CGR", 1, "NORMALE", "demande-timeout"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated SOAP timeout");
    }

    private static JAXBSource payload(Object response) throws JAXBException {
        return new JAXBSource(JAXBContext.newInstance(response.getClass()), response);
    }

    private static StreamSource fault(String faultCode, String reason) {
        String envelope = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <soap:Fault>
                      <faultcode>soap:%s</faultcode>
                      <faultstring>%s</faultstring>
                    </soap:Fault>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(faultCode, reason);
        return new StreamSource(new StringReader(envelope));
    }
}
