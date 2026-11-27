package tn.iit.blood.service_don_stock.soap;

/**
 * Thrown when the CNTS SOAP service returns StockCentralInsuffisantFault.
 * Mapped to gRPC STOCK_INSUFFISANT_PARTOUT in DonStockGrpcService.
 */
public class StockInsuffisantException extends RuntimeException {

    public StockInsuffisantException(String message) {
        super(message);
    }
}
