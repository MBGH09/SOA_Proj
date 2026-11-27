package tn.iit.blood.cnts.exception;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/**
 * Thrown when neither CRTS nor CNTS can supply the requested quantity (R16).
 * Maps to StockCentralInsuffisantFault defined in cnts.xsd.
 */
@SoapFault(faultCode = FaultCode.SERVER, faultStringOrReason = "Stock central insuffisant")
public class StockCentralInsuffisantException extends RuntimeException {
    public StockCentralInsuffisantException(String message) {
        super(message);
    }
}
