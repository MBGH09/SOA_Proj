package com.service_demandes.exception;

/**
 * Un service aval (service-donneurs, service-don-stock) est injoignable.
 * Mappée en HTTP 503 par {@link GlobalExceptionHandler}.
 */
public class ServiceIndisponibleException extends RuntimeException {

    public ServiceIndisponibleException(String message) {
        super(message);
    }

    public ServiceIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
