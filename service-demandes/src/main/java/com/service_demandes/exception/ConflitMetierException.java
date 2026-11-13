package com.service_demandes.exception;

/** L'etat actuel de la ressource ne permet pas l'operation : repondue en 409. */
public class ConflitMetierException extends RuntimeException {

    public ConflitMetierException(String message) {
        super(message);
    }
}
