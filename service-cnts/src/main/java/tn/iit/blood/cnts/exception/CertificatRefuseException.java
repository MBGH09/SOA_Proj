package tn.iit.blood.cnts.exception;

/**
 * Thrown when a certificate cannot be delivered (don unknown or not declared).
 * @SoapFault annotation is added in S5-07.
 */
public class CertificatRefuseException extends RuntimeException {
    public CertificatRefuseException(String message) {
        super(message);
    }
}
