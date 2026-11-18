CREATE TABLE donneur (
    id BIGSERIAL PRIMARY KEY,
    cin VARCHAR(8) UNIQUE NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    date_naissance DATE NOT NULL,
    poids_kg INTEGER NOT NULL,
    groupe_sanguin VARCHAR(10) NOT NULL,
    ville VARCHAR(100),
    telephone VARCHAR(20),
    premier_don BOOLEAN DEFAULT TRUE
);

CREATE TABLE ajournement (
    id BIGSERIAL PRIMARY KEY,
    donneur_id BIGINT NOT NULL,
    motif VARCHAR(50) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    CONSTRAINT fk_ajournement_donneur
        FOREIGN KEY (donneur_id)
        REFERENCES donneur(id)
);

CREATE TABLE centre (
    id BIGSERIAL PRIMARY KEY,
    nom VARCHAR(200) NOT NULL,
    type VARCHAR(50) NOT NULL,
    ville VARCHAR(100),
    date_debut DATE,
    date_fin DATE
);
CREATE TABLE creneau (
    id BIGSERIAL PRIMARY KEY,
    centre_id BIGINT NOT NULL,
    debut TIMESTAMP NOT NULL,
    fin TIMESTAMP NOT NULL,
    capacite INTEGER NOT NULL,
    CONSTRAINT fk_creneau_centre
        FOREIGN KEY (centre_id)
        REFERENCES centre(id)
);

CREATE TABLE rendez_vous (
    id BIGSERIAL PRIMARY KEY,
    donneur_id BIGINT NOT NULL,
    centre_id BIGINT NOT NULL,
    creneau_id BIGINT NOT NULL,
    canal VARCHAR(20),
    statut VARCHAR(20),
    CONSTRAINT fk_rdv_donneur FOREIGN KEY (donneur_id) REFERENCES donneur(id),
    CONSTRAINT fk_rdv_centre FOREIGN KEY (centre_id) REFERENCES centre(id),
    CONSTRAINT fk_rdv_creneau FOREIGN KEY (creneau_id) REFERENCES creneau(id)
);

CREATE TABLE don (
    id BIGSERIAL PRIMARY KEY,
    donneur_id BIGINT NOT NULL,
    centre_id BIGINT NOT NULL,
    rendez_vous_id BIGINT,

    etape VARCHAR(50),
    decision VARCHAR(50),

    statut_synchro VARCHAR(30) DEFAULT 'A_SYNCHRONISER',

    numero_don_national VARCHAR(100),

    certificat_statut VARCHAR(30) DEFAULT 'A_EMETTRE',
    numero_certificat VARCHAR(100),
    date_certificat DATE,

    CONSTRAINT fk_don_donneur
        FOREIGN KEY (donneur_id)
        REFERENCES donneur(id),

    CONSTRAINT fk_don_centre
        FOREIGN KEY (centre_id)
        REFERENCES centre(id),

    CONSTRAINT fk_don_rdv
        FOREIGN KEY (rendez_vous_id)
        REFERENCES rendez_vous(id)
);

CREATE TABLE etape_don (
    id BIGSERIAL PRIMARY KEY,

    don_id BIGINT NOT NULL,

    etape VARCHAR(50) NOT NULL,

    date_heure TIMESTAMP NOT NULL,

    donnees JSONB,

    CONSTRAINT fk_etape_don
        FOREIGN KEY (don_id)
        REFERENCES don(id)
);


CREATE UNIQUE INDEX uk_rdv_donneur_planifie
ON rendez_vous(donneur_id, creneau_id)
WHERE statut = 'PLANIFIE';