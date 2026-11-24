-- =============================================================
-- V4 – Seed data for reservation and prelevement tables
-- =============================================================
-- Covers every possible status so each demo scenario has
-- pre-existing data to show without running the full flow.
--
-- reservation statuses:
--   RESERVEE_STOCK_LOCAL        → hospital had enough local stock
--   APPROVISIONNEMENT_COMMANDE  → order placed with CNTS/CRTS, en route
--   LIVREE                      → delivery simulator confirmed arrival
--   STOCK_INSUFFISANT_PARTOUT   → nobody could supply → pénurie
--   A_CONFIRMER                 → CNTS was down, retry job will re-send
--
-- prelevement statuses:
--   A_DECLARER          → CNTS call failed, retry pending
--   QUALIFICATION_EN_COURS → declared to CNTS, waiting in lab
--   QUALIFIE            → qualification done, blood in disponible stock
-- =============================================================


-- =============================================================
-- RESERVATIONS
-- =============================================================

-- 1. Local stock satisfied (CHU Habib Bourguiba, A_POS CGR)
INSERT INTO reservation (demande_id, lieu_id, groupe_sanguin, composant, quantite, statut,
                         reference, centre_source, eta_minutes, created_at, updated_at)
VALUES ('dem-hbs-001', 'lieu-chu-hbs', 'A_POS', 'CGR', 4,
        'RESERVEE_STOCK_LOCAL',
        NULL, NULL, NULL,
        NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours');

-- 2. Local stock satisfied (CHU Habib Bourguiba, O_POS PLASMA)
INSERT INTO reservation (demande_id, lieu_id, groupe_sanguin, composant, quantite, statut,
                         reference, centre_source, eta_minutes, created_at, updated_at)
VALUES ('dem-hbs-002', 'lieu-chu-hbs', 'O_POS', 'PLASMA', 2,
        'RESERVEE_STOCK_LOCAL',
        NULL, NULL, NULL,
        NOW() - INTERVAL '5 hours', NOW() - INTERVAL '5 hours');

-- 3. Local stock satisfied (CHU Hedi Chaker, B_POS CGR)
INSERT INTO reservation (demande_id, lieu_id, groupe_sanguin, composant, quantite, statut,
                         reference, centre_source, eta_minutes, created_at, updated_at)
VALUES ('dem-hk-001', 'lieu-chu-hk', 'B_POS', 'CGR', 3,
        'RESERVEE_STOCK_LOCAL',
        NULL, NULL, NULL,
        NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour');

-- 4. Central order placed, not yet delivered (CHU Habib Bourguiba, O_NEG CGR)
--    O_NEG is low at HBS so it went to CRTS Sfax
INSERT INTO reservation (demande_id, lieu_id, groupe_sanguin, composant, quantite, statut,
                         reference, centre_source, eta_minutes, created_at, updated_at)
VALUES ('dem-hbs-003', 'lieu-chu-hbs', 'O_NEG', 'CGR', 5,
        'APPROVISIONNEMENT_COMMANDE',
        'APP-2026-000001', 'CRTS Sfax', 45,
        NOW() - INTERVAL '20 minutes', NOW() - INTERVAL '20 minutes');

-- 5. Central order placed, not yet delivered (CHU Hedi Chaker, AB_NEG CGR)
--    AB_NEG is critically low at HK, went straight to CNTS Tunis
INSERT INTO reservation (demande_id, lieu_id, groupe_sanguin, composant, quantite, statut,
                         reference, centre_source, eta_minutes, created_at, updated_at)
VALUES ('dem-hk-002', 'lieu-chu-hk', 'AB_NEG', 'CGR', 2,
        'APPROVISIONNEMENT_COMMANDE',
        'APP-2026-000002', 'CNTS Tunis', 90,
        NOW() - INTERVAL '10 minutes', NOW() - INTERVAL '10 minutes');

-- 6. Delivered (CHU Hedi Chaker, A_POS PLASMA) — was ordered, now arrived
INSERT INTO reservation (demande_id, lieu_id, groupe_sanguin, composant, quantite, statut,
                         reference, centre_source, eta_minutes, created_at, updated_at)
VALUES ('dem-hk-003', 'lieu-chu-hk', 'A_POS', 'PLASMA', 6,
        'LIVREE',
        'APP-2026-000003', 'CRTS Sfax', 30,
        NOW() - INTERVAL '3 hours', NOW() - INTERVAL '2 hours');

-- 7. Delivered (CHU Habib Bourguiba, B_POS PLAQUETTES)
INSERT INTO reservation (demande_id, lieu_id, groupe_sanguin, composant, quantite, statut,
                         reference, centre_source, eta_minutes, created_at, updated_at)
VALUES ('dem-hbs-004', 'lieu-chu-hbs', 'B_POS', 'PLAQUETTES', 3,
        'LIVREE',
        'APP-2026-000004', 'CRTS Sfax', 20,
        NOW() - INTERVAL '6 hours', NOW() - INTERVAL '5 hours');

-- 8. Stock insuffisant partout (CHU Hedi Chaker, AB_NEG PLAQUETTES)
--    0 platelets at HK, none at CRTS or CNTS either → pénurie
INSERT INTO reservation (demande_id, lieu_id, groupe_sanguin, composant, quantite, statut,
                         reference, centre_source, eta_minutes, created_at, updated_at)
VALUES ('dem-hk-004', 'lieu-chu-hk', 'AB_NEG', 'PLAQUETTES', 2,
        'STOCK_INSUFFISANT_PARTOUT',
        NULL, NULL, NULL,
        NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '30 minutes');

-- 9. Stock insuffisant partout (Clinique Exemple, O_NEG PLAQUETTES)
--    Clinic has no stock of its own and central stock was exhausted
INSERT INTO reservation (demande_id, lieu_id, groupe_sanguin, composant, quantite, statut,
                         reference, centre_source, eta_minutes, created_at, updated_at)
VALUES ('dem-clin-001', 'lieu-clinique-sfax', 'O_NEG', 'PLAQUETTES', 4,
        'STOCK_INSUFFISANT_PARTOUT',
        NULL, NULL, NULL,
        NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour');

-- 10. A_CONFIRMER — CNTS was down when this order was placed (CHU Habib Bourguiba, B_NEG CGR)
--     RetryConfirmerJob will re-send this when CNTS comes back up
INSERT INTO reservation (demande_id, lieu_id, groupe_sanguin, composant, quantite, statut,
                         reference, centre_source, eta_minutes, created_at, updated_at)
VALUES ('dem-hbs-005', 'lieu-chu-hbs', 'B_NEG', 'CGR', 3,
        'A_CONFIRMER',
        NULL, NULL, NULL,
        NOW() - INTERVAL '15 minutes', NOW() - INTERVAL '15 minutes');


-- =============================================================
-- PRELEVEMENTS
-- =============================================================

-- 1. Fully qualified — donation complete, certificate issued
--    Matches the finished donation seeded by service-donneurs (Student 3)
INSERT INTO prelevement (don_id, donneur_id, centre_id, groupe_sanguin, volume_ml,
                         statut, numero_don_national, numero_certificat, date_certificat,
                         created_at, updated_at)
VALUES ('don-seed-001', 'donneur-seed-001', 'lieu-crts-sfax', 'A_POS', 450,
        'QUALIFIE',
        'DN-2026-000001', 'CERT-2026-000001', '2026-11-20',
        NOW() - INTERVAL '4 days', NOW() - INTERVAL '3 days');

-- 2. Fully qualified, certificate issued — different donor, O_POS
INSERT INTO prelevement (don_id, donneur_id, centre_id, groupe_sanguin, volume_ml,
                         statut, numero_don_national, numero_certificat, date_certificat,
                         created_at, updated_at)
VALUES ('don-seed-002', 'donneur-seed-002', 'lieu-crts-sfax', 'O_POS', 450,
        'QUALIFIE',
        'DN-2026-000002', 'CERT-2026-000002', '2026-11-21',
        NOW() - INTERVAL '3 days', NOW() - INTERVAL '2 days');

-- 3. Fully qualified, certificate issued — B_POS at CNTS Tunis
INSERT INTO prelevement (don_id, donneur_id, centre_id, groupe_sanguin, volume_ml,
                         statut, numero_don_national, numero_certificat, date_certificat,
                         created_at, updated_at)
VALUES ('don-seed-003', 'donneur-seed-003', 'lieu-cnts-tunis', 'B_POS', 450,
        'QUALIFIE',
        'DN-2026-000003', 'CERT-2026-000003', '2026-11-22',
        NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day');

-- 4. Qualification in progress — declared to CNTS, waiting in lab (O_NEG, mobile collection)
--    QualificationSimulatorJob will move this to QUALIFIE after DELAI_QUALIFICATION_SEC
INSERT INTO prelevement (don_id, donneur_id, centre_id, groupe_sanguin, volume_ml,
                         statut, numero_don_national, numero_certificat, date_certificat,
                         created_at, updated_at)
VALUES ('don-seed-004', 'donneur-seed-004', 'lieu-crts-sfax', 'O_NEG', 450,
        'QUALIFICATION_EN_COURS',
        'DN-2026-000004', NULL, NULL,
        NOW() - INTERVAL '10 minutes', NOW() - INTERVAL '10 minutes');

-- 5. Qualification in progress — AB_POS at CHU Habib Bourguiba
INSERT INTO prelevement (don_id, donneur_id, centre_id, groupe_sanguin, volume_ml,
                         statut, numero_don_national, numero_certificat, date_certificat,
                         created_at, updated_at)
VALUES ('don-seed-005', 'donneur-seed-005', 'lieu-chu-hbs', 'AB_POS', 450,
        'QUALIFICATION_EN_COURS',
        'DN-2026-000005', NULL, NULL,
        NOW() - INTERVAL '5 minutes', NOW() - INTERVAL '5 minutes');

-- 6. A_DECLARER — CNTS was unreachable when this donation was collected
--    RetryConfirmerJob (via EnregistrerPrelevement retry in service-donneurs) will fix this
INSERT INTO prelevement (don_id, donneur_id, centre_id, groupe_sanguin, volume_ml,
                         statut, numero_don_national, numero_certificat, date_certificat,
                         created_at, updated_at)
VALUES ('don-seed-006', 'donneur-seed-006', 'lieu-crts-sfax', 'A_NEG', 450,
        'A_DECLARER',
        NULL, NULL, NULL,
        NOW() - INTERVAL '25 minutes', NOW() - INTERVAL '25 minutes');

-- 7. A_DECLARER — another pending declaration (B_NEG, mobile collection point)
INSERT INTO prelevement (don_id, donneur_id, centre_id, groupe_sanguin, volume_ml,
                         statut, numero_don_national, numero_certificat, date_certificat,
                         created_at, updated_at)
VALUES ('don-seed-007', 'donneur-seed-007', 'lieu-mobile-crts-sfax', 'B_NEG', 450,
        'QUALIFIE',
        'DN-2026-000007', 'CERT-2026-000007', '2026-11-23',
        NOW() - INTERVAL '1 day', NOW() - INTERVAL '20 hours');
