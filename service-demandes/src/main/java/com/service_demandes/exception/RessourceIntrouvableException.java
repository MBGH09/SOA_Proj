package com.service_demandes.exception;

/** Ressource inconnue : repondue en 404. */
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
