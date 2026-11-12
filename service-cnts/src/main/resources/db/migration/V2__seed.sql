-- =============================================================================
-- S5-04 : Données initiales (seed) du service-cnts
-- =============================================================================
-- Stock initial réaliste pour les démos :
--   • CNTS Tunis (CNTS-TN) : stock national de référence
--   • CRTS Sfax  (CRTS-SFX): stock régional (intentionnellement bas en O_NEG)
--
-- Convention des groupes : A_POS, A_NEG, B_POS, B_NEG, AB_POS, AB_NEG, O_POS, O_NEG
-- Convention des composants : SANG_TOTAL, PLASMA, PLAQUETTES
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Stock CNTS Tunis (centre national)
-- ---------------------------------------------------------------------------
INSERT INTO stock_central (centre_id, groupe_sanguin, composant, quantite) VALUES
-- SANG_TOTAL
('CNTS-TN', 'A_POS',  'SANG_TOTAL', 150),
('CNTS-TN', 'A_NEG',  'SANG_TOTAL',  40),
('CNTS-TN', 'B_POS',  'SANG_TOTAL',  90),
('CNTS-TN', 'B_NEG',  'SANG_TOTAL',  25),
('CNTS-TN', 'AB_POS', 'SANG_TOTAL',  35),
('CNTS-TN', 'AB_NEG', 'SANG_TOTAL',  10),
('CNTS-TN', 'O_POS',  'SANG_TOTAL', 200),
('CNTS-TN', 'O_NEG',  'SANG_TOTAL',   5),  -- Intentionnellement bas pour démo D5
-- PLASMA
('CNTS-TN', 'A_POS',  'PLASMA',      80),
('CNTS-TN', 'A_NEG',  'PLASMA',      20),
('CNTS-TN', 'B_POS',  'PLASMA',      50),
('CNTS-TN', 'B_NEG',  'PLASMA',      12),
('CNTS-TN', 'AB_POS', 'PLASMA',      18),
('CNTS-TN', 'AB_NEG', 'PLASMA',       5),
('CNTS-TN', 'O_POS',  'PLASMA',     100),
('CNTS-TN', 'O_NEG',  'PLASMA',       3),  -- Intentionnellement bas pour démo D5
-- PLAQUETTES
('CNTS-TN', 'A_POS',  'PLAQUETTES',  60),
('CNTS-TN', 'A_NEG',  'PLAQUETTES',  15),
('CNTS-TN', 'B_POS',  'PLAQUETTES',  35),
('CNTS-TN', 'B_NEG',  'PLAQUETTES',   8),
('CNTS-TN', 'AB_POS', 'PLAQUETTES',  12),
('CNTS-TN', 'AB_NEG', 'PLAQUETTES',   3),
('CNTS-TN', 'O_POS',  'PLAQUETTES',  80),
('CNTS-TN', 'O_NEG',  'PLAQUETTES',   2)   -- Intentionnellement bas pour démo D5
ON CONFLICT (centre_id, groupe_sanguin, composant) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Stock CRTS Sfax (centre régional)
-- ---------------------------------------------------------------------------
INSERT INTO stock_central (centre_id, groupe_sanguin, composant, quantite) VALUES
-- SANG_TOTAL
('CRTS-SFX', 'A_POS',  'SANG_TOTAL',  60),
('CRTS-SFX', 'A_NEG',  'SANG_TOTAL',  15),
('CRTS-SFX', 'B_POS',  'SANG_TOTAL',  40),
('CRTS-SFX', 'B_NEG',  'SANG_TOTAL',   8),
('CRTS-SFX', 'AB_POS', 'SANG_TOTAL',  12),
('CRTS-SFX', 'AB_NEG', 'SANG_TOTAL',   3),
('CRTS-SFX', 'O_POS',  'SANG_TOTAL',  80),
('CRTS-SFX', 'O_NEG',  'SANG_TOTAL',   1),  -- Très bas → démo D5 : CRTS refuse, CNTS aussi → StockInsuffisant
-- PLASMA
('CRTS-SFX', 'A_POS',  'PLASMA',      30),
('CRTS-SFX', 'A_NEG',  'PLASMA',       8),
('CRTS-SFX', 'B_POS',  'PLASMA',      20),
('CRTS-SFX', 'B_NEG',  'PLASMA',       4),
('CRTS-SFX', 'AB_POS', 'PLASMA',       6),
('CRTS-SFX', 'AB_NEG', 'PLASMA',       1),
('CRTS-SFX', 'O_POS',  'PLASMA',      40),
('CRTS-SFX', 'O_NEG',  'PLASMA',       0),  -- Zéro → StockInsuffisant garanti
-- PLAQUETTES
('CRTS-SFX', 'A_POS',  'PLAQUETTES',  20),
('CRTS-SFX', 'A_NEG',  'PLAQUETTES',   5),
('CRTS-SFX', 'B_POS',  'PLAQUETTES',  15),
('CRTS-SFX', 'B_NEG',  'PLAQUETTES',   2),
('CRTS-SFX', 'AB_POS', 'PLAQUETTES',   4),
('CRTS-SFX', 'AB_NEG', 'PLAQUETTES',   1),
('CRTS-SFX', 'O_POS',  'PLAQUETTES',  30),
('CRTS-SFX', 'O_NEG',  'PLAQUETTES',   0)   -- Zéro → StockInsuffisant garanti
ON CONFLICT (centre_id, groupe_sanguin, composant) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Prélèvement de référence (seed)
--    Coordonné avec Student 3 (service-don) et Student 4 (service-don-stock)
--    don_id_local = 'don-seed-001' est l'ID que Student 4 utilisera dans ses tests
-- ---------------------------------------------------------------------------
INSERT INTO prelevement_declare (
    numero_don_national,
    don_id_local,
    groupe_sanguin,
    volume_ml,
    centre_id,
    date_prelevement,
    statut
) VALUES (
    'DN-2026-000001',   -- Numéro national de référence pour les démos
    'don-seed-001',     -- ID local utilisé par Student 4 dans ses tests (coordonné)
    'O_POS',
    450,
    'CRTS-SFX',
    '2026-11-01',
    'VALIDE'
) ON CONFLICT (don_id_local) DO NOTHING;

-- Mettre à jour la séquence pour éviter les collisions avec le seed
SELECT SETVAL('seq_don_national', 1, true);

-- ---------------------------------------------------------------------------
-- 4. Certificat de référence (seed)
--    Lié au prélèvement don-seed-001
-- ---------------------------------------------------------------------------
INSERT INTO certificat (
    numero_certificat,
    numero_don_national,
    don_id_local,
    centre_id,
    date_don,
    date_emission,
    statut
) VALUES (
    'CERT-2026-000001', -- Numéro de certificat de référence pour les démos
    'DN-2026-000001',
    'don-seed-001',
    'CRTS-SFX',
    '2026-11-01',
    '2026-11-01',
    'EMIS'
) ON CONFLICT (don_id_local) DO NOTHING;

-- Mettre à jour la séquence pour éviter les collisions
SELECT SETVAL('seq_certificat', 1, true);

