package tn.iit.blood.cnts.exception;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/**
 * Thrown when a certificate cannot be delivered (don unknown, not VALIDE, or donIdLocal mismatch).
 * Maps to CertificatRefuseFault defined in cnts.xsd.
 * R24: only after VALIDE status. R25: idempotent by donIdLocal.
 */
@SoapFault(faultCode = FaultCode.SERVER, faultStringOrReason = "Certificat refusé")
public class CertificatRefuseException extends RuntimeException {
    public CertificatRefuseException(String message) {
        super(message);
    }
}
