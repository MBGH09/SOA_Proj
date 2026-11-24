package tn.iit.blood.service_don_stock.soap;

/**
 * Thrown when the CNTS SOAP service returns CertificatRefuseFault.
 * Mapped to gRPC FAILED_PRECONDITION in DonStockGrpcService.
 */
public class CertificatRefuseException extends RuntimeException {

    public CertificatRefuseException(String message) {
        super(message);
    }
}
