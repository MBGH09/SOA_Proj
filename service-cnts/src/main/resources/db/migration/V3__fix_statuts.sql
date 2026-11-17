-- =============================================================================
-- V3 : Alignement des statuts avec la spécification S5-05
-- =============================================================================
-- V1 avait des statuts qui ne correspondent pas à ce qu'attend S5-05 :
--   • prelevement_declare.statut : manquait QUALIFICATION_EN_COURS (statut initial)
--   • certificat.statut          : EMIS renommé en VALIDE (cohérence avec la spec)
--
-- V1 est déjà appliquée/partagée → on corrige via ALTER, pas en réécrivant V1.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. prelevement_declare : ajouter QUALIFICATION_EN_COURS, changer le défaut
-- ---------------------------------------------------------------------------
-- Supprimer l'ancienne contrainte
ALTER TABLE prelevement_declare DROP CONSTRAINT IF EXISTS chk_statut_prelev;

-- Recréer avec les 3 statuts du cycle de vie :
--   QUALIFICATION_EN_COURS → prélèvement reçu, en attente de validation CNTS
--   VALIDE                 → validé par le CNTS
--   REJETE                 → rejeté (volume incorrect, date future, etc.)
ALTER TABLE prelevement_declare
    ADD CONSTRAINT chk_statut_prelev
    CHECK (statut IN ('QUALIFICATION_EN_COURS', 'VALIDE', 'REJETE'));

-- Le statut initial d'un nouveau prélèvement = QUALIFICATION_EN_COURS
ALTER TABLE prelevement_declare
    ALTER COLUMN statut SET DEFAULT 'QUALIFICATION_EN_COURS';

-- ---------------------------------------------------------------------------
-- 2. certificat : EMIS → VALIDE (cohérence avec la spec)
-- ---------------------------------------------------------------------------
ALTER TABLE certificat DROP CONSTRAINT IF EXISTS chk_statut_cert;

-- Renommer EMIS en VALIDE dans les données existantes (seed V2)
UPDATE certificat SET statut = 'VALIDE' WHERE statut = 'EMIS';

-- Recréer la contrainte avec VALIDE au lieu de EMIS
ALTER TABLE certificat
    ADD CONSTRAINT chk_statut_cert
    CHECK (statut IN ('VALIDE', 'REFUSE'));

-- Mettre à jour le défaut
ALTER TABLE certificat
    ALTER COLUMN statut SET DEFAULT 'VALIDE';

