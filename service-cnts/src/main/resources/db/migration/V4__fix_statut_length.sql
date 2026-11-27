-- =============================================================================
-- V4 : Élargir prelevement_declare.statut de VARCHAR(20) à VARCHAR(30)
-- =============================================================================
-- QUALIFICATION_EN_COURS = 22 caractères → ne tient pas dans VARCHAR(20).
-- Toute nouvelle déclaration échouait avec une erreur de troncature.
-- =============================================================================
ALTER TABLE prelevement_declare
    ALTER COLUMN statut TYPE VARCHAR(30);
