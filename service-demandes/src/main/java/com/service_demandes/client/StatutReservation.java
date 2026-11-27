package com.service_demandes.client;

/** Reponses possibles de Don & Stock (champ statut de ReservationResponse dans donstock.proto). */
public enum StatutReservation {
    RESERVEE_STOCK_LOCAL,
    APPROVISIONNEMENT_COMMANDE,
    STOCK_INSUFFISANT_PARTOUT
}
