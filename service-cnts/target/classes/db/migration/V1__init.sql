-- =============================================================================
-- S5-04 : Schéma initial du service-cnts
-- Service : CNTS (Centre National de Transfusion Sanguine)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Séquences pour la génération de numéros uniques nationaux
-- ---------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS seq_don_national
    START WITH 1 INCREMENT BY 1 NO MAXVALUE CACHE 1;

CREATE SEQUENCE IF NOT EXISTS seq_approvisionnement
    START WITH 1 INCREMENT BY 1 NO MAXVALUE CACHE 1;

CREATE SEQUENCE IF NOT EXISTS seq_certificat
    START WITH 1 INCREMENT BY 1 NO MAXVALUE CACHE 1;

-- ---------------------------------------------------------------------------
-- 2. Table stock_central
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_central (
    id               BIGSERIAL    PRIMARY KEY,
    centre_id        VARCHAR(20)  NOT NULL,
    groupe_sanguin   VARCHAR(10)  NOT NULL,
    -- composant conforme au XSD : CGR | PLASMA | PLAQUETTES
    composant        VARCHAR(20)  NOT NULL,
    quantite         INTEGER      NOT NULL DEFAULT 0,
    mise_a_jour      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_quantite_positive CHECK (quantite >= 0),
    CONSTRAINT chk_composant CHECK (composant IN ('CGR', 'PLASMA', 'PLAQUETTES')),
    CONSTRAINT uq_stock_centre_groupe_composant UNIQUE (centre_id, groupe_sanguin, composant)
);

-- ---------------------------------------------------------------------------
-- 3. Table prelevement_declare
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prelevement_declare (
    id                    BIGSERIAL    PRIMARY KEY,
    numero_don_national   VARCHAR(30)  NOT NULL,
    don_id_local          VARCHAR(50)  NOT NULL,
    groupe_sanguin        VARCHAR(10)  NOT NULL,
    volume_ml             INTEGER      NOT NULL,
    centre_id             VARCHAR(20)  NOT NULL,
    date_prelevement      DATE         NOT NULL,
    statut                VARCHAR(20)  NOT NULL DEFAULT 'VALIDE',
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_don_id_local     UNIQUE (don_id_local),
    CONSTRAINT uq_numero_don_nat   UNIQUE (numero_don_national),
    CONSTRAINT chk_volume          CHECK (volume_ml = 450),
    CONSTRAINT chk_statut_prelev   CHECK (statut IN ('VALIDE', 'REJETE'))
);

-- ---------------------------------------------------------------------------
-- 4. Table approvisionnement
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS approvisionnement (
    id                 BIGSERIAL    PRIMARY KEY,
    reference          VARCHAR(30)  NOT NULL,
    demande_id         VARCHAR(50)  NOT NULL,
    hopital_id         VARCHAR(50)  NOT NULL,
    groupe_sanguin     VARCHAR(10)  NOT NULL,
    -- composant conforme au XSD : CGR | PLASMA | PLAQUETTES
    composant          VARCHAR(20)  NOT NULL,
    quantite_demandee  INTEGER      NOT NULL,
    quantite_accordee  INTEGER      NOT NULL DEFAULT 0,
    centre_source      VARCHAR(20),
    delai_minutes      INTEGER,
    statut             VARCHAR(20)  NOT NULL DEFAULT 'EN_COURS',
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_demande_id       UNIQUE (demande_id),
    CONSTRAINT uq_reference_appro  UNIQUE (reference),
    CONSTRAINT chk_statut_appro    CHECK (statut IN ('ACCORDEE', 'REFUSEE', 'EN_COURS')),
    CONSTRAINT chk_composant_appro CHECK (composant IN ('CGR', 'PLASMA', 'PLAQUETTES')),
    CONSTRAINT chk_qte_accordee    CHECK (quantite_accordee >= 0)
);

-- ---------------------------------------------------------------------------
-- 5. Table certificat
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS certificat (
    id                   BIGSERIAL    PRIMARY KEY,
    numero_certificat    VARCHAR(30)  NOT NULL,
    numero_don_national  VARCHAR(30)  NOT NULL,
    don_id_local         VARCHAR(50)  NOT NULL,
    centre_id            VARCHAR(20)  NOT NULL,
    date_don             DATE         NOT NULL,
    date_emission        DATE         NOT NULL DEFAULT CURRENT_DATE,
    statut               VARCHAR(20)  NOT NULL DEFAULT 'EMIS',
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_numero_certificat    UNIQUE (numero_certificat),
    CONSTRAINT uq_certificat_don_local UNIQUE (don_id_local),
    CONSTRAINT chk_statut_cert         CHECK (statut IN ('EMIS', 'REFUSE'))
);

-- ---------------------------------------------------------------------------
-- 6. Index
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_stock_centre    ON stock_central (centre_id);
CREATE INDEX IF NOT EXISTS idx_stock_groupe    ON stock_central (groupe_sanguin);
CREATE INDEX IF NOT EXISTS idx_prelev_groupe   ON prelevement_declare (groupe_sanguin);
CREATE INDEX IF NOT EXISTS idx_appro_hopital   ON approvisionnement (hopital_id);

