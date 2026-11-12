-- =============================================================================
-- S5-04 : Schéma initial du service-cnts
-- Service : CNTS (Centre National de Transfusion Sanguine)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Séquences pour la génération de numéros uniques nationaux
-- ---------------------------------------------------------------------------

-- Numéro national de don  : DN-{YYYY}-{000001}
CREATE SEQUENCE IF NOT EXISTS seq_don_national
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    CACHE 1;

-- Référence d'approvisionnement : APPRO-{YYYY}-{000001}
CREATE SEQUENCE IF NOT EXISTS seq_approvisionnement
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    CACHE 1;

-- Numéro de certificat de don : CERT-{YYYY}-{000001}
CREATE SEQUENCE IF NOT EXISTS seq_certificat
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    CACHE 1;

-- ---------------------------------------------------------------------------
-- 2. Table stock_central
--    Représente les unités de sang disponibles par centre, groupe et composant
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_central (
    id               BIGSERIAL    PRIMARY KEY,
    centre_id        VARCHAR(20)  NOT NULL,   -- ex: 'CNTS-TN', 'CRTS-SFX'
    groupe_sanguin   VARCHAR(10)  NOT NULL,   -- A_POS, A_NEG, B_POS, B_NEG, AB_POS, AB_NEG, O_POS, O_NEG
    composant        VARCHAR(20)  NOT NULL,   -- SANG_TOTAL, PLASMA, PLAQUETTES
    quantite         INTEGER      NOT NULL DEFAULT 0,
    mise_a_jour      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_quantite_positive CHECK (quantite >= 0),
    CONSTRAINT uq_stock_centre_groupe_composant UNIQUE (centre_id, groupe_sanguin, composant)
);

-- ---------------------------------------------------------------------------
-- 3. Table prelevement_declare
--    Enregistre chaque prélèvement déclaré par service-don-stock (Student 4)
--    Idempotence : don_id_local est UNIQUE (R24)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prelevement_declare (
    id                    BIGSERIAL    PRIMARY KEY,
    numero_don_national   VARCHAR(30)  NOT NULL,  -- Généré par le CNTS : DN-YYYY-NNNNNN
    don_id_local          VARCHAR(50)  NOT NULL,  -- ID fourni par service-don-stock (clé d'idempotence)
    groupe_sanguin        VARCHAR(10)  NOT NULL,
    volume_ml             INTEGER      NOT NULL,  -- Doit être 450 ml (R20)
    centre_id             VARCHAR(20)  NOT NULL,  -- Centre déclarant
    date_prelevement      DATE         NOT NULL,  -- Ne peut pas être dans le futur (R21)
    statut                VARCHAR(20)  NOT NULL DEFAULT 'VALIDE',
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_don_id_local     UNIQUE (don_id_local),
    CONSTRAINT uq_numero_don_nat   UNIQUE (numero_don_national),
    CONSTRAINT chk_volume          CHECK (volume_ml = 450),
    CONSTRAINT chk_statut_prelev   CHECK (statut IN ('VALIDE', 'REJETE'))
);

-- ---------------------------------------------------------------------------
-- 4. Table approvisionnement
--    Enregistre chaque demande d'approvisionnement (service-don-stock → CNTS)
--    Idempotence : demande_id est UNIQUE (Gap 5)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS approvisionnement (
    id                 BIGSERIAL    PRIMARY KEY,
    reference          VARCHAR(30)  NOT NULL,  -- Généré par le CNTS : APPRO-YYYY-NNNNNN
    demande_id         VARCHAR(50)  NOT NULL,  -- Clé d'idempotence fournie par service-don-stock
    hopital_id         VARCHAR(50)  NOT NULL,  -- Hôpital destinataire
    groupe_sanguin     VARCHAR(10)  NOT NULL,
    composant          VARCHAR(20)  NOT NULL,
    quantite_demandee  INTEGER      NOT NULL,
    quantite_accordee  INTEGER      NOT NULL DEFAULT 0,
    centre_source      VARCHAR(20),            -- Quel centre a fourni le stock
    delai_minutes      INTEGER,                -- Délai de livraison estimé
    statut             VARCHAR(20)  NOT NULL DEFAULT 'EN_COURS',
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_demande_id       UNIQUE (demande_id),
    CONSTRAINT uq_reference_appro  UNIQUE (reference),
    CONSTRAINT chk_statut_appro    CHECK (statut IN ('ACCORDEE', 'REFUSEE', 'EN_COURS')),
    CONSTRAINT chk_qte_accordee    CHECK (quantite_accordee >= 0)
);

-- ---------------------------------------------------------------------------
-- 5. Table certificat
--    Certificat de don émis par le CNTS pour chaque prélèvement validé
--    Idempotence : don_id_local est UNIQUE (R25)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS certificat (
    id                   BIGSERIAL    PRIMARY KEY,
    numero_certificat    VARCHAR(30)  NOT NULL,  -- Généré : CERT-YYYY-NNNNNN
    numero_don_national  VARCHAR(30)  NOT NULL,  -- Référence au prélèvement
    don_id_local         VARCHAR(50)  NOT NULL,  -- Clé d'idempotence
    centre_id            VARCHAR(20)  NOT NULL,
    date_don             DATE         NOT NULL,
    date_emission        DATE         NOT NULL DEFAULT CURRENT_DATE,
    statut               VARCHAR(20)  NOT NULL DEFAULT 'EMIS',
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_numero_certificat  UNIQUE (numero_certificat),
    CONSTRAINT uq_certificat_don_local UNIQUE (don_id_local),
    CONSTRAINT chk_statut_cert       CHECK (statut IN ('EMIS', 'REFUSE'))
);

-- ---------------------------------------------------------------------------
-- 6. Index pour les recherches fréquentes
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_stock_centre    ON stock_central (centre_id);
CREATE INDEX IF NOT EXISTS idx_stock_groupe    ON stock_central (groupe_sanguin);
CREATE INDEX IF NOT EXISTS idx_prelev_groupe   ON prelevement_declare (groupe_sanguin);
CREATE INDEX IF NOT EXISTS idx_appro_hopital   ON approvisionnement (hopital_id);

