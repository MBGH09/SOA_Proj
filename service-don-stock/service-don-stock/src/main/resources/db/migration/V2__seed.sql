-- =============================================================
-- V2 – Seed data for service-don-stock
-- =============================================================

-- ---- Lieux ----
INSERT INTO lieu (id, nom, type, region, stock_local) VALUES
    ('lieu-cnts-tunis',      'CNTS Tunis',                       'CNTS',               'Tunis',  TRUE),
    ('lieu-crts-sfax',       'CRTS Sfax',                        'CRTS',               'Sfax',   TRUE),
    ('lieu-chu-hbs',         'CHU Habib Bourguiba Sfax',         'HOPITAL',            'Sfax',   TRUE),
    ('lieu-chu-hk',          'CHU Hedi Chaker Sfax',             'HOPITAL',            'Sfax',   TRUE),
    ('lieu-clinique-sfax',   'Clinique Exemple Sfax',            'CLINIQUE_SANS_STOCK','Sfax',   FALSE),
    ('lieu-mobile-crts-sfax','Collecte Mobile CRTS Sfax',        'COLLECTE_MOBILE',    'Sfax',   FALSE);

-- ---- Helper: seed stock_niveau for a given lieu ----
-- CNTS Tunis – high stock everywhere
INSERT INTO stock_niveau (lieu_id, groupe_sanguin, composant, disponible, reserve, en_qualification, en_transit, seuil_alerte)
SELECT 'lieu-cnts-tunis', g.g, c.c, 80, 0, 0, 0, 10
FROM (VALUES ('A_POS'),('A_NEG'),('B_POS'),('B_NEG'),('AB_POS'),('AB_NEG'),('O_POS'),('O_NEG')) AS g(g),
     (VALUES ('CGR'),('PLASMA'),('PLAQUETTES')) AS c(c);

-- CRTS Sfax
INSERT INTO stock_niveau (lieu_id, groupe_sanguin, composant, disponible, reserve, en_qualification, en_transit, seuil_alerte)
SELECT 'lieu-crts-sfax', g.g, c.c, 50, 0, 0, 0, 10
FROM (VALUES ('A_POS'),('A_NEG'),('B_POS'),('B_NEG'),('AB_POS'),('AB_NEG'),('O_POS'),('O_NEG')) AS g(g),
     (VALUES ('CGR'),('PLASMA'),('PLAQUETTES')) AS c(c);

-- CHU Habib Bourguiba Sfax – O_NEG intentionally low
INSERT INTO stock_niveau (lieu_id, groupe_sanguin, composant, disponible, reserve, en_qualification, en_transit, seuil_alerte)
SELECT 'lieu-chu-hbs', g.g, c.c,
       CASE WHEN g.g = 'O_NEG' THEN 3 ELSE 25 END,
       0, 0, 0, 10
FROM (VALUES ('A_POS'),('A_NEG'),('B_POS'),('B_NEG'),('AB_POS'),('AB_NEG'),('O_POS'),('O_NEG')) AS g(g),
     (VALUES ('CGR'),('PLASMA'),('PLAQUETTES')) AS c(c);

-- CHU Hedi Chaker Sfax – AB_NEG intentionally low
INSERT INTO stock_niveau (lieu_id, groupe_sanguin, composant, disponible, reserve, en_qualification, en_transit, seuil_alerte)
SELECT 'lieu-chu-hk', g.g, c.c,
       CASE WHEN g.g = 'AB_NEG' THEN 2 ELSE 20 END,
       0, 0, 0, 10
FROM (VALUES ('A_POS'),('A_NEG'),('B_POS'),('B_NEG'),('AB_POS'),('AB_NEG'),('O_POS'),('O_NEG')) AS g(g),
     (VALUES ('CGR'),('PLASMA'),('PLAQUETTES')) AS c(c);
