package tn.iit.blood.service_don_stock.soap;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;
import tn.iit.blood.donstock.soap.client.*;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDate;

/**
 * S4-12 — SOAP client for service-cnts.
 *
 * Wraps the three operations with Resilience4j retry (3 attempts, exponential
 * backoff from 500 ms) and a circuit breaker, both named "cnts" and configured
 * in application.properties.
 *
 * Fault handling:
 *   PrelevementInvalideFault   → PrelevementInvalideException  (never retried)
 *   StockCentralInsuffisantFault → StockInsuffisantException   (never retried)
 *   CertificatRefuseFault      → CertificatRefuseException     (never retried)
 *
 * Faults are caught before Resilience4j can retry them: they extend
 * RuntimeException but are excluded from the retry via
 * resilience4j.retry.instances.cnts.ignore-exceptions in application.properties.
 */
@Component
public class CntsWebServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CntsWebServiceClient.class);

    private final WebServiceTemplate webServiceTemplate;

    @Value("${cnts.url:http://service-cnts:8084/ws}")
    private String cntsUrl;

    public CntsWebServiceClient(WebServiceTemplate cntsWebServiceTemplate) {
        this.webServiceTemplate = cntsWebServiceTemplate;
    }

    // ================================================================
    // Operation 1 — enregistrerPrelevement
    // ================================================================

    @Retry(name = "cnts")
    @CircuitBreaker(name = "cnts")
    public EnregistrerPrelevementResponse enregistrerPrelevement(
            String donIdLocal,
            String groupeSanguin,
            int volumeMl,
            String centreId,
            LocalDate datePrelevement) {

        EnregistrerPrelevementRequest req = new EnregistrerPrelevementRequest();
        req.setDonIdLocal(donIdLocal);
        req.setGroupeSanguin(GroupeSanguin.fromValue(groupeSanguin));
        req.setVolumeMl(volumeMl);
        req.setCentreId(centreId);
        req.setDatePrelevement(toXmlDate(datePrelevement));

        log.debug("SOAP enregistrerPrelevement → donIdLocal={}", donIdLocal);
        try {
            EnregistrerPrelevementResponse resp = (EnregistrerPrelevementResponse)
                    webServiceTemplate.marshalSendAndReceive(cntsUrl, req);
            log.info("SOAP enregistrerPrelevement ← numeroDonNational={}", resp.getNumeroDonNational());
            return resp;
        } catch (SoapFaultClientException e) {
            handleFault(e);
            throw e; // unreachable — handleFault always throws
        }
    }

    // ================================================================
    // Operation 2 — demanderApprovisionnement
    // ================================================================

    @Retry(name = "cnts")
    @CircuitBreaker(name = "cnts")
    public DemanderApprovisionnementResponse demanderApprovisionnement(
            String hopitalId,
            String region,
            String groupeSanguin,
            String composant,
            int quantite,
            String urgence,
            String demandeId) {

        DemanderApprovisionnementRequest req = new DemanderApprovisionnementRequest();
        req.setHopitalId(hopitalId);
        req.setRegion(region != null ? region : "");
        req.setGroupeSanguin(GroupeSanguin.fromValue(groupeSanguin));
        req.setComposant(Composant.fromValue(composant));
        req.setQuantite(quantite);
        req.setUrgence(Urgence.fromValue(urgence));
        req.setDemandeId(demandeId);

        log.debug("SOAP demanderApprovisionnement → demandeId={}", demandeId);
        try {
            DemanderApprovisionnementResponse resp = (DemanderApprovisionnementResponse)
                    webServiceTemplate.marshalSendAndReceive(cntsUrl, req);
            log.info("SOAP demanderApprovisionnement ← reference={} centreSource={}",
                    resp.getReference(), resp.getCentreSource());
            return resp;
        } catch (SoapFaultClientException e) {
            handleFault(e);
            throw e;
        }
    }

    // ================================================================
    // Operation 3 — delivrerCertificatDon
    // ================================================================

    @Retry(name = "cnts")
    @CircuitBreaker(name = "cnts")
    public DelivrerCertificatDonResponse delivrerCertificatDon(
            String donIdLocal,
            String numeroDonNational,
            String donneurRef,
            String centreId,
            LocalDate dateDon) {

        DelivrerCertificatDonRequest req = new DelivrerCertificatDonRequest();
        req.setDonIdLocal(donIdLocal);
        req.setNumeroDonNational(numeroDonNational);
        req.setDonneurRef(donneurRef);
        req.setCentreId(centreId);
        req.setDateDon(toXmlDate(dateDon));

        log.debug("SOAP delivrerCertificatDon → donIdLocal={}", donIdLocal);
        try {
            DelivrerCertificatDonResponse resp = (DelivrerCertificatDonResponse)
                    webServiceTemplate.marshalSendAndReceive(cntsUrl, req);
            log.info("SOAP delivrerCertificatDon ← numeroCertificat={}", resp.getNumeroCertificat());
            return resp;
        } catch (SoapFaultClientException e) {
            handleFault(e);
            throw e;
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    /**
     * Inspects the SOAP fault local name and throws the matching domain exception.
     * Domain faults must NOT be retried, so they are excluded from the retry
     * via resilience4j.retry.instances.cnts.ignore-exceptions.
     */
    private void handleFault(SoapFaultClientException e) {
        String faultName = e.getSoapFault() != null
                ? e.getSoapFault().getFaultCode().getLocalPart()
                : "";
        log.warn("SOAP fault received: {} — {}", faultName, e.getFaultStringOrReason());

        if (faultName.contains("PrelevementInvalide")) {
            throw new PrelevementInvalideException(e.getFaultStringOrReason());
        }
        if (faultName.contains("StockCentralInsuffisant")) {
            throw new StockInsuffisantException(e.getFaultStringOrReason());
        }
        if (faultName.contains("CertificatRefuse")) {
            throw new CertificatRefuseException(e.getFaultStringOrReason());
        }
        // Unknown fault — rethrow as-is so Resilience4j sees it as a failure
        throw e;
    }

    private XMLGregorianCalendar toXmlDate(LocalDate date) {
        try {
            return DatatypeFactory.newInstance()
                    .newXMLGregorianCalendar(date.toString());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot convert date: " + date, e);
        }
    }
}
