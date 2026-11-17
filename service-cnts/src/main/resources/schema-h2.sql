-- H2 sequences matching V1__init.sql (used only when running with -Dspring-boot.run.profiles=h2)
-- Flyway is OFF on H2 profile, so Spring creates these via sql.init before JPA starts.
CREATE SEQUENCE IF NOT EXISTS seq_don_national     START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_certificat       START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_approvisionnement START WITH 1 INCREMENT BY 1;

-- Tables (mirror of V1__init.sql, simplified for local dev)
CREATE TABLE IF NOT EXISTS stock_central (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    centre_id      VARCHAR(20)  NOT NULL,
    groupe_sanguin VARCHAR(10)  NOT NULL,
    composant      VARCHAR(20)  NOT NULL,
    quantite       INTEGER      NOT NULL DEFAULT 0,
    mise_a_jour    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stock_centre_groupe_composant UNIQUE (centre_id, groupe_sanguin, composant)
);

CREATE TABLE IF NOT EXISTS prelevement_declare (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero_don_national VARCHAR(30)  NOT NULL UNIQUE,
    don_id_local        VARCHAR(50)  NOT NULL UNIQUE,
    groupe_sanguin      VARCHAR(10)  NOT NULL,
    volume_ml           INTEGER      NOT NULL,
    centre_id           VARCHAR(20)  NOT NULL,
    date_prelevement    DATE         NOT NULL,
    statut              VARCHAR(30)  NOT NULL DEFAULT 'QUALIFICATION_EN_COURS',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS certificat (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero_certificat   VARCHAR(30)  NOT NULL UNIQUE,
    numero_don_national VARCHAR(30)  NOT NULL,
    don_id_local        VARCHAR(50)  NOT NULL UNIQUE,
    centre_id           VARCHAR(20)  NOT NULL,
    date_don            DATE         NOT NULL,
    date_emission       DATE         NOT NULL DEFAULT CURRENT_DATE,
    statut              VARCHAR(20)  NOT NULL DEFAULT 'VALIDE',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS approvisionnement (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference         VARCHAR(30)  NOT NULL UNIQUE,
    demande_id        VARCHAR(50)  NOT NULL UNIQUE,
    hopital_id        VARCHAR(50)  NOT NULL,
    groupe_sanguin    VARCHAR(10)  NOT NULL,
    composant         VARCHAR(20)  NOT NULL,
    quantite_demandee INTEGER      NOT NULL,
    quantite_accordee INTEGER      NOT NULL DEFAULT 0,
    centre_source     VARCHAR(20),
    delai_minutes     INTEGER,
    statut            VARCHAR(20)  NOT NULL DEFAULT 'EN_COURS',
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);
