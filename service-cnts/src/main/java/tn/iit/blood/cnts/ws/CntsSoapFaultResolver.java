package tn.iit.blood.cnts.ws;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.AbstractEndpointExceptionResolver;
import org.springframework.ws.soap.SoapBody;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapMessage;
import tn.iit.blood.cnts.exception.CertificatRefuseException;
import tn.iit.blood.cnts.exception.PrelevementInvalideException;
import tn.iit.blood.cnts.exception.StockCentralInsuffisantException;
import tn.iit.blood.cnts.ws.generated.CertificatRefuseFault;
import tn.iit.blood.cnts.ws.generated.PrelevementInvalideFault;
import tn.iit.blood.cnts.ws.generated.StockCentralInsuffisantFault;

import javax.xml.namespace.QName;
import java.util.Locale;

/**
 * Custom SOAP fault resolver — S5-05 / S5-07.
 *
 * Maps PrelevementInvalideException and CertificatRefuseException to their
 * typed XSD fault payloads (PrelevementInvalideFault, CertificatRefuseFault)
 * serialized in the SOAP <detail> element.
 *
 * Without this, Spring-WS emits a generic fault string with no <detail>,
 * and generated clients cannot deserialize the declared fault type.
 *
 * Uses JAXBContext directly (not Jaxb2Marshaller) to avoid Spring bean
 * initialization order issues inside the exception resolver lifecycle.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CntsSoapFaultResolver extends AbstractEndpointExceptionResolver {

    private static final String NS = "http://iit.tn/soa/cnts";

    public CntsSoapFaultResolver() {
        setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    /**
     * Static JAXBContext initialized once at class load.
     * Covers both fault types declared in cnts.xsd.
     */
    private static final JAXBContext JAXB_CTX;
    static {
        try {
            JAXB_CTX = JAXBContext.newInstance(
                    PrelevementInvalideFault.class,
                    CertificatRefuseFault.class,
                    StockCentralInsuffisantFault.class);
        } catch (JAXBException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    protected boolean resolveExceptionInternal(MessageContext mc, Object endpoint, Exception ex) {
        if (!(mc.getResponse() instanceof SoapMessage response)) {
            return false;
        }

        SoapBody body = response.getSoapBody();

        if (ex instanceof PrelevementInvalideException pie) {
            SoapFault fault = body.addServerOrReceiverFault("Prélèvement invalide", Locale.FRENCH);
            PrelevementInvalideFault bean = new PrelevementInvalideFault();
            bean.setCode("PRELEVEMENT_INVALIDE");
            bean.setMessage(pie.getMessage());
            addDetail(fault, new JAXBElement<>(
                    new QName(NS, "PrelevementInvalideFault"),
                    PrelevementInvalideFault.class, bean));
            return true;
        }

        if (ex instanceof CertificatRefuseException cre) {
            SoapFault fault = body.addServerOrReceiverFault("Certificat refusé", Locale.FRENCH);
            CertificatRefuseFault bean = new CertificatRefuseFault();
            bean.setCode("CERTIFICAT_REFUSE");
            bean.setMessage(cre.getMessage());
            addDetail(fault, new JAXBElement<>(
                    new QName(NS, "CertificatRefuseFault"),
                    CertificatRefuseFault.class, bean));
            return true;
        }

        if (ex instanceof StockCentralInsuffisantException scie) {
            SoapFault fault = body.addServerOrReceiverFault("Stock central insuffisant", Locale.FRENCH);
            StockCentralInsuffisantFault bean = new StockCentralInsuffisantFault();
            bean.setCode("STOCK_CENTRAL_INSUFFISANT");
            bean.setMessage(scie.getMessage());
            addDetail(fault, new JAXBElement<>(
                    new QName(NS, "StockCentralInsuffisantFault"),
                    StockCentralInsuffisantFault.class, bean));
            return true;
        }

        return false;
    }

    private void addDetail(SoapFault fault, JAXBElement<?> jaxbElement) {
        try {
            Marshaller marshaller = JAXB_CTX.createMarshaller();
            marshaller.marshal(jaxbElement, fault.addFaultDetail().getResult());
        } catch (Exception e) {
            // Log at ERROR with stack trace so the problem is never invisible
            log.error("Failed to serialize SOAP fault detail [{}]: {}",
                    jaxbElement.getName().getLocalPart(), e.getMessage(), e);
        }
    }
}
