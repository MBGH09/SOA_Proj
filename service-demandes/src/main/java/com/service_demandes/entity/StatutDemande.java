package com.service_demandes.entity;

public enum StatutDemande {
    EN_ATTENTE,
    SATISFAITE,
    APPROVISIONNEMENT_EN_COURS,
    EN_PENURIE,
    ANNULEE;

    /** Une demande ouverte peut encore etre annulee ou traitee. */
    public boolean estOuverte() {
        return this == EN_ATTENTE || this == APPROVISIONNEMENT_EN_COURS || this == EN_PENURIE;
    }
}
