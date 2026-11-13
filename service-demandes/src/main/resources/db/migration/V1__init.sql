CREATE TABLE demandes (
    id UUID PRIMARY KEY,

    hopital_id VARCHAR(100) NOT NULL,
    hopital_nom VARCHAR(255) NOT NULL,
    patient_ref VARCHAR(100) NOT NULL,

    motif VARCHAR(255),

    groupe_sanguin VARCHAR(10) NOT NULL,
    composant VARCHAR(50) NOT NULL,

    quantite_poches INTEGER NOT NULL,

    urgence VARCHAR(50) NOT NULL,

    statut VARCHAR(50) NOT NULL,

    compensation_demandee INTEGER,
    compensation_recue INTEGER,

    date_creation TIMESTAMP NOT NULL
);

CREATE TABLE appels_don (
    id UUID PRIMARY KEY,

    demande_id UUID NOT NULL,

    groupe_sanguin VARCHAR(10) NOT NULL,
    composant VARCHAR(50) NOT NULL,

    statut VARCHAR(50) NOT NULL,

    date_creation TIMESTAMP,

    CONSTRAINT fk_appel_demande
        FOREIGN KEY (demande_id)
        REFERENCES demandes(id)
        ON DELETE CASCADE
);

CREATE TABLE compensations (
    id UUID PRIMARY KEY,

    demande_id UUID NOT NULL,

    numero_certificat VARCHAR(100) UNIQUE NOT NULL,

    date_enregistrement TIMESTAMP,

    CONSTRAINT fk_compensation_demande
        FOREIGN KEY (demande_id)
        REFERENCES demandes(id)
        ON DELETE CASCADE
);