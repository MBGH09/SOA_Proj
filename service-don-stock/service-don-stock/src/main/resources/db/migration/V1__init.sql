-- =============================================================
-- V1 – Schema for service-don-stock
-- =============================================================

-- ---- lieu (places: CNTS, CRTS, hospital banks, clinics, mobile) ----
CREATE TABLE lieu (
    id          VARCHAR(36)  PRIMARY KEY,
    nom         VARCHAR(255) NOT NULL,
    type        VARCHAR(50)  NOT NULL,   -- CNTS | CRTS | HOPITAL | CLINIQUE_SANS_STOCK | COLLECTE_MOBILE
    region      VARCHAR(100),
    stock_local BOOLEAN      NOT NULL DEFAULT FALSE
);

-- ---- stock_niveau (one row per lieu x blood_group x component) ----
CREATE TABLE stock_niveau (
    lieu_id          VARCHAR(36)  NOT NULL REFERENCES lieu(id),
    groupe_sanguin   VARCHAR(10)  NOT NULL,  -- A_POS|A_NEG|B_POS|B_NEG|AB_POS|AB_NEG|O_POS|O_NEG
    composant        VARCHAR(20)  NOT NULL,  -- CGR|PLASMA|PLAQUETTES
    disponible       INTEGER      NOT NULL DEFAULT 0,
    reserve          INTEGER      NOT NULL DEFAULT 0,
    en_qualification INTEGER      NOT NULL DEFAULT 0,
    en_transit       INTEGER      NOT NULL DEFAULT 0,
    seuil_alerte     INTEGER      NOT NULL DEFAULT 10,
    PRIMARY KEY (lieu_id, groupe_sanguin, composant)
);

-- ---- reservation ----
CREATE TABLE reservation (
    id              BIGSERIAL    PRIMARY KEY,
    demande_id      VARCHAR(100) NOT NULL UNIQUE,   -- idempotency key
    lieu_id         VARCHAR(36)  NOT NULL REFERENCES lieu(id),
    groupe_sanguin  VARCHAR(10)  NOT NULL,
    composant       VARCHAR(20)  NOT NULL,
    quantite        INTEGER      NOT NULL,
    statut          VARCHAR(50)  NOT NULL,
    -- filled when APPROVISIONNEMENT_COMMANDE
    reference       VARCHAR(100),
    centre_source   VARCHAR(255),
    eta_minutes     INTEGER,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ---- prelevement (collected pouches declared to CNTS) ----
CREATE TABLE prelevement (
    id                   BIGSERIAL    PRIMARY KEY,
    don_id               VARCHAR(100) NOT NULL UNIQUE,   -- idempotency key
    donneur_id           VARCHAR(100) NOT NULL,
    centre_id            VARCHAR(36)  NOT NULL,
    groupe_sanguin       VARCHAR(10)  NOT NULL,
    volume_ml            INTEGER      NOT NULL,
    statut               VARCHAR(50)  NOT NULL DEFAULT 'A_DECLARER',
    -- filled after CNTS declaration
    numero_don_national  VARCHAR(100),
    -- filled after certificate delivery
    numero_certificat    VARCHAR(100),
    date_certificat      DATE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
