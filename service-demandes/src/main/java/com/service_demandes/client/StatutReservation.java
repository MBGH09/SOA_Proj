package com.service_demandes.client;

/**
 * Statuts de réservation de Don &amp; Stock (enum {@code StatutReservation} du proto).
 * Gap 4 : LIVREE et EN_ATTENTE_CONFIRMATION sont inclus.
 */
public enum StatutReservation {
    RESERVEE_STOCK_LOCAL,
    APPROVISIONNEMENT_COMMANDE,
    STOCK_INSUFFISANT_PARTOUT,
    /** La livraison par le simulateur est confirmée. */
    LIVREE,
    /** SOAP CNTS injoignable, ordre stocké comme A_CONFIRMER. */
    EN_ATTENTE_CONFIRMATION
}
