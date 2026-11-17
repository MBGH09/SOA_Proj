package com.service_demandes.client;

/**
 * Don & Stock ne peut pas repondre : UNAVAILABLE, DEADLINE_EXCEEDED ou disjoncteur ouvert.
 * La demande reste alors EN_ATTENTE et la reconciliation la terminera plus tard.
 */
public class DonStockIndisponibleException extends RuntimeException {

    public DonStockIndisponibleException(String message) {
        super(message);
    }

    public DonStockIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
