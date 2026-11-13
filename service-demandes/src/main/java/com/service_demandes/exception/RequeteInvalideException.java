package com.service_demandes.exception;

/** Regle de validation metier non couverte par les annotations : repondue en 400. */
public class RequeteInvalideException extends RuntimeException {

    public RequeteInvalideException(String message) {
        super(message);
    }
}
