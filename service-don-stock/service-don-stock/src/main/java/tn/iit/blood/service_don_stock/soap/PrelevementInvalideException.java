package tn.iit.blood.service_don_stock.soap;

/**
 * Thrown when the CNTS SOAP service returns PrelevementInvalideFault.
 * Mapped to gRPC INVALID_ARGUMENT in DonStockGrpcService.
 */
public class PrelevementInvalideException extends RuntimeException {

    public PrelevementInvalideException(String message) {
        super(message);
    }
}
