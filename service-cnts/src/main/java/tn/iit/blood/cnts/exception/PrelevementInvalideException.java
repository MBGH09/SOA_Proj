package tn.iit.blood.cnts.exception;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/**
 * Thrown when a prelevement is invalid (R8: volume ≠ 450 ml, unknown group, future date).
 * Maps to PrelevementInvalideFault defined in cnts.xsd.
 */
@SoapFault(faultCode = FaultCode.SERVER, faultStringOrReason = "Prélèvement invalide")
public class PrelevementInvalideException extends RuntimeException {
    public PrelevementInvalideException(String message) {
        super(message);
    }
}
