-- =============================================================
-- V3 – Realistic stock levels per hospital
-- =============================================================
-- Replaces the flat V2 levels with values that reflect each
-- hospital's size, patient profile and component shelf-life:
--
--   CHU Habib Bourguiba Sfax  (lieu-chu-hbs)
--     Large teaching hospital: surgery, trauma, oncology.
--     High consumption of O_POS and A_POS (universal / most common
--     Tunisian groups).  O_NEG kept intentionally low (rare, hard to
--     replenish).  Platelets kept thin because they expire in 5–7 days.
--
--   CHU Hedi Chaker Sfax  (lieu-chu-hk)
--     Specialised referral centre: neurology, cardiology.
--     AB_NEG and B_NEG are very rare — set below the alert threshold
--     so the dashboard shows red from the start of the demo.
--     Plasma stock is higher than average (cardiac surgery demand).
--
-- Alert thresholds are set per component:
--   CGR (red cells, 42-day shelf-life)  → seuil_alerte = 8
--   PLASMA (1-year shelf-life)          → seuil_alerte = 6
--   PLAQUETTES (5–7-day shelf-life)     → seuil_alerte = 4
--
-- The V2 rows are updated, not inserted again (the PK already exists).
-- =============================================================

-- ============================================================
-- CHU Habib Bourguiba Sfax  (lieu-chu-hbs)
-- ============================================================

-- CGR — seuil_alerte 8
UPDATE stock_niveau SET disponible =  6, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'O_NEG'   AND composant = 'CGR';
UPDATE stock_niveau SET disponible = 28, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'O_POS'   AND composant = 'CGR';
UPDATE stock_niveau SET disponible = 22, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'A_POS'   AND composant = 'CGR';
UPDATE stock_niveau SET disponible = 10, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'A_NEG'   AND composant = 'CGR';
UPDATE stock_niveau SET disponible = 14, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'B_POS'   AND composant = 'CGR';
UPDATE stock_niveau SET disponible =  7, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'B_NEG'   AND composant = 'CGR';
UPDATE stock_niveau SET disponible = 12, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'AB_POS'  AND composant = 'CGR';
UPDATE stock_niveau SET disponible =  5, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'AB_NEG'  AND composant = 'CGR';

-- PLASMA — seuil_alerte 6
UPDATE stock_niveau SET disponible =  4, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'O_NEG'   AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible = 20, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'O_POS'   AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible = 18, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'A_POS'   AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible =  8, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'A_NEG'   AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible = 11, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'B_POS'   AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible =  5, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'B_NEG'   AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible =  9, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'AB_POS'  AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible =  3, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'AB_NEG'  AND composant = 'PLASMA';

-- PLAQUETTES — seuil_alerte 4 (short shelf-life: kept lean)
UPDATE stock_niveau SET disponible =  2, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'O_NEG'   AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  9, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'O_POS'   AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  8, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'A_POS'   AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  4, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'A_NEG'   AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  5, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'B_POS'   AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  2, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'B_NEG'   AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  4, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'AB_POS'  AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  1, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hbs' AND groupe_sanguin = 'AB_NEG'  AND composant = 'PLAQUETTES';

-- ============================================================
-- CHU Hedi Chaker Sfax  (lieu-chu-hk)
-- ============================================================

-- CGR — seuil_alerte 8
UPDATE stock_niveau SET disponible =  5, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'O_NEG'   AND composant = 'CGR';
UPDATE stock_niveau SET disponible = 24, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'O_POS'   AND composant = 'CGR';
UPDATE stock_niveau SET disponible = 19, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'A_POS'   AND composant = 'CGR';
UPDATE stock_niveau SET disponible =  9, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'A_NEG'   AND composant = 'CGR';
UPDATE stock_niveau SET disponible = 13, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'B_POS'   AND composant = 'CGR';
UPDATE stock_niveau SET disponible =  4, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'B_NEG'   AND composant = 'CGR';
UPDATE stock_niveau SET disponible = 10, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'AB_POS'  AND composant = 'CGR';
UPDATE stock_niveau SET disponible =  2, seuil_alerte = 8 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'AB_NEG'  AND composant = 'CGR';

-- PLASMA — seuil_alerte 6 (higher than HBS: cardiac surgery demand)
UPDATE stock_niveau SET disponible =  3, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'O_NEG'   AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible = 22, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'O_POS'   AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible = 20, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'A_POS'   AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible =  9, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'A_NEG'   AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible = 12, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'B_POS'   AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible =  3, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'B_NEG'   AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible =  8, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'AB_POS'  AND composant = 'PLASMA';
UPDATE stock_niveau SET disponible =  1, seuil_alerte = 6 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'AB_NEG'  AND composant = 'PLASMA';

-- PLAQUETTES — seuil_alerte 4
UPDATE stock_niveau SET disponible =  2, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'O_NEG'   AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  8, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'O_POS'   AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  7, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'A_POS'   AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  3, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'A_NEG'   AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  5, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'B_POS'   AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  1, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'B_NEG'   AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  3, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'AB_POS'  AND composant = 'PLAQUETTES';
UPDATE stock_niveau SET disponible =  0, seuil_alerte = 4 WHERE lieu_id = 'lieu-chu-hk' AND groupe_sanguin = 'AB_NEG'  AND composant = 'PLAQUETTES';

-- ============================================================
-- Also tighten CRTS and CNTS thresholds per component
-- (V2 left seuil_alerte = 10 for everything)
-- ============================================================

UPDATE stock_niveau SET seuil_alerte = 15 WHERE lieu_id = 'lieu-cnts-tunis' AND composant = 'CGR';
UPDATE stock_niveau SET seuil_alerte = 12 WHERE lieu_id = 'lieu-cnts-tunis' AND composant = 'PLASMA';
UPDATE stock_niveau SET seuil_alerte =  8 WHERE lieu_id = 'lieu-cnts-tunis' AND composant = 'PLAQUETTES';

UPDATE stock_niveau SET seuil_alerte = 12 WHERE lieu_id = 'lieu-crts-sfax'  AND composant = 'CGR';
UPDATE stock_niveau SET seuil_alerte =  8 WHERE lieu_id = 'lieu-crts-sfax'  AND composant = 'PLASMA';
UPDATE stock_niveau SET seuil_alerte =  5 WHERE lieu_id = 'lieu-crts-sfax'  AND composant = 'PLAQUETTES';
