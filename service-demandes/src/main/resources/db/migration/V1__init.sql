-- service-demandes : base demandes-db
-- Tables de l'etude (section 3.2). Il n'y a AUCUNE colonne pour le nom du patient, seulement patient_ref (R19).
-- Les dates sont stockees en UTC (voir hibernate.jdbc.time_zone dans application.properties).

CREATE TABLE demande (
    id                          UUID PRIMARY KEY,
    hopital_id                  VARCHAR(100)  NOT NULL,
    hopital_nom                 VARCHAR(255)  NOT NULL,
    patient_ref                 VARCHAR(50)   NOT NULL,
    motif                       VARCHAR(20)   NOT NULL,
    groupe_sanguin              VARCHAR(10)   NOT NULL,
    composant                   VARCHAR(20)   NOT NULL,
    quantite_poches             INTEGER       NOT NULL,
    urgence                     VARCHAR(20)   NOT NULL,
    statut                      VARCHAR(30)   NOT NULL,
    reference_approvisionnement VARCHAR(100),
    compensation_demandee       INTEGER,
    compensation_recue          INTEGER       NOT NULL DEFAULT 0,
    date_creation               TIMESTAMP NOT NULL,
    CONSTRAINT ck_demande_quantite CHECK (quantite_poches >= 1),
    CONSTRAINT ck_demande_compensation_recue CHECK (compensation_recue >= 0)
);

CREATE INDEX idx_demande_statut ON demande (statut);
CREATE INDEX idx_demande_hopital ON demande (hopital_id);

CREATE TABLE appel_don (
    id               UUID PRIMARY KEY,
    demande_id       UUID,
    groupe_sanguin   VARCHAR(10)  NOT NULL,
    composant        VARCHAR(20)  NOT NULL,
    centre_cible_id  VARCHAR(100),
    statut           VARCHAR(20)  NOT NULL,
    date_ouverture   TIMESTAMP NOT NULL,
    date_fermeture   TIMESTAMP,
    -- la demande est facultative : un appel peut etre ouvert a la main
    CONSTRAINT fk_appel_demande FOREIGN KEY (demande_id) REFERENCES demande (id) ON DELETE SET NULL
);

CREATE INDEX idx_appel_statut ON appel_don (statut, groupe_sanguin, composant);

CREATE TABLE compensation (
    id                  UUID PRIMARY KEY,
    demande_id          UUID         NOT NULL,
    numero_certificat   VARCHAR(100) NOT NULL,
    date_enregistrement TIMESTAMP NOT NULL,
    -- un numero de certificat ne compte qu'une fois (R26)
    CONSTRAINT uk_compensation_numero UNIQUE (numero_certificat),
    CONSTRAINT fk_compensation_demande FOREIGN KEY (demande_id) REFERENCES demande (id) ON DELETE CASCADE
);

CREATE INDEX idx_compensation_demande ON compensation (demande_id);
