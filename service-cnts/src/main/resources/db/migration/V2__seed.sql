-- =============================================================================
-- S5-04 : Données initiales (seed) du service-cnts
-- =============================================================================
-- IMPORTANT : composant conforme au XSD → CGR (et non SANG_TOTAL)
-- Valeurs autorisées : CGR | PLASMA | PLAQUETTES
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Stock CNTS Tunis
-- ---------------------------------------------------------------------------
INSERT INTO stock_central (centre_id, groupe_sanguin, composant, quantite) VALUES
-- CGR (Concentré de Globules Rouges) — conforme XSD
('CNTS-TUNIS', 'A_POS',  'CGR', 150),
('CNTS-TUNIS', 'A_NEG',  'CGR',  40),
('CNTS-TUNIS', 'B_POS',  'CGR',  90),
('CNTS-TUNIS', 'B_NEG',  'CGR',  25),
('CNTS-TUNIS', 'AB_POS', 'CGR',  35),
('CNTS-TUNIS', 'AB_NEG', 'CGR',  10),
('CNTS-TUNIS', 'O_POS',  'CGR', 200),
('CNTS-TUNIS', 'O_NEG',  'CGR',   5),  -- Bas pour démo D5
-- PLASMA
('CNTS-TUNIS', 'A_POS',  'PLASMA',  80),
('CNTS-TUNIS', 'A_NEG',  'PLASMA',  20),
('CNTS-TUNIS', 'B_POS',  'PLASMA',  50),
('CNTS-TUNIS', 'B_NEG',  'PLASMA',  12),
('CNTS-TUNIS', 'AB_POS', 'PLASMA',  18),
('CNTS-TUNIS', 'AB_NEG', 'PLASMA',   5),
('CNTS-TUNIS', 'O_POS',  'PLASMA', 100),
('CNTS-TUNIS', 'O_NEG',  'PLASMA',   3),  -- Bas pour démo D5
-- PLAQUETTES
('CNTS-TUNIS', 'A_POS',  'PLAQUETTES',  60),
('CNTS-TUNIS', 'A_NEG',  'PLAQUETTES',  15),
('CNTS-TUNIS', 'B_POS',  'PLAQUETTES',  35),
('CNTS-TUNIS', 'B_NEG',  'PLAQUETTES',   8),
('CNTS-TUNIS', 'AB_POS', 'PLAQUETTES',  12),
('CNTS-TUNIS', 'AB_NEG', 'PLAQUETTES',   3),
('CNTS-TUNIS', 'O_POS',  'PLAQUETTES',  80),
('CNTS-TUNIS', 'O_NEG',  'PLAQUETTES',   2)   -- Bas pour démo D5
ON CONFLICT (centre_id, groupe_sanguin, composant) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Stock CRTS Sfax
-- ---------------------------------------------------------------------------
INSERT INTO stock_central (centre_id, groupe_sanguin, composant, quantite) VALUES
-- CGR
('CRTS-SFAX', 'A_POS',  'CGR',  60),
('CRTS-SFAX', 'A_NEG',  'CGR',  15),
('CRTS-SFAX', 'B_POS',  'CGR',  40),
('CRTS-SFAX', 'B_NEG',  'CGR',   8),
('CRTS-SFAX', 'AB_POS', 'CGR',  12),
('CRTS-SFAX', 'AB_NEG', 'CGR',   3),
('CRTS-SFAX', 'O_POS',  'CGR',  80),
('CRTS-SFAX', 'O_NEG',  'CGR',   1),  -- Très bas → démo D5 : StockInsuffisant
-- PLASMA
('CRTS-SFAX', 'A_POS',  'PLASMA',  30),
('CRTS-SFAX', 'A_NEG',  'PLASMA',   8),
('CRTS-SFAX', 'B_POS',  'PLASMA',  20),
('CRTS-SFAX', 'B_NEG',  'PLASMA',   4),
('CRTS-SFAX', 'AB_POS', 'PLASMA',   6),
('CRTS-SFAX', 'AB_NEG', 'PLASMA',   1),
('CRTS-SFAX', 'O_POS',  'PLASMA',  40),
('CRTS-SFAX', 'O_NEG',  'PLASMA',   0),  -- Zéro → StockInsuffisant garanti
-- PLAQUETTES
('CRTS-SFAX', 'A_POS',  'PLAQUETTES',  20),
('CRTS-SFAX', 'A_NEG',  'PLAQUETTES',   5),
('CRTS-SFAX', 'B_POS',  'PLAQUETTES',  15),
('CRTS-SFAX', 'B_NEG',  'PLAQUETTES',   2),
('CRTS-SFAX', 'AB_POS', 'PLAQUETTES',   4),
('CRTS-SFAX', 'AB_NEG', 'PLAQUETTES',   1),
('CRTS-SFAX', 'O_POS',  'PLAQUETTES',  30),
('CRTS-SFAX', 'O_NEG',  'PLAQUETTES',   0)  -- Zéro → StockInsuffisant garanti
ON CONFLICT (centre_id, groupe_sanguin, composant) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Prélèvement de référence (seed)
-- ---------------------------------------------------------------------------
INSERT INTO prelevement_declare (
    numero_don_national, don_id_local, groupe_sanguin,
    volume_ml, centre_id, date_prelevement, statut
) VALUES (
    'DN-2026-000001', 'don-seed-001', 'O_POS',
    450, 'CRTS-SFX', '2026-11-01', 'VALIDE'
) ON CONFLICT (don_id_local) DO NOTHING;

SELECT SETVAL('seq_don_national', 1, true);

-- ---------------------------------------------------------------------------
-- 4. Certificat de référence (seed)
-- ---------------------------------------------------------------------------
INSERT INTO certificat (
    numero_certificat, numero_don_national, don_id_local,
    centre_id, date_don, date_emission, statut
) VALUES (
    'CERT-2026-000001', 'DN-2026-000001', 'don-seed-001',
    'CRTS-SFX', '2026-11-01', '2026-11-01', 'EMIS'
) ON CONFLICT (don_id_local) DO NOTHING;

SELECT SETVAL('seq_certificat', 1, true);

