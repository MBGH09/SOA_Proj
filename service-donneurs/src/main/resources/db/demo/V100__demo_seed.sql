-- Fictional demo data. These people and places are not real.
INSERT INTO donneur (cin, nom, prenom, date_naissance, poids_kg, groupe_sanguin, ville, telephone, premier_don)
VALUES
    ('10000001', 'Trabelsi', 'Amine', DATE '1994-04-12', 72, 'O_POS', 'Sfax', '20000001', TRUE),
    ('10000002', 'Ben Salem', 'Nour', DATE '1988-09-03', 65, 'A_POS', 'Sfax', '20000002', FALSE),
    ('10000003', 'Khelifi', 'Hedi', DATE '1991-12-19', 80, 'B_POS', 'Tunis', '20000003', FALSE),
    ('10000004', 'Mansour', 'Leila', DATE '2001-01-20', 48, 'A_NEG', 'Sfax', '20000004', TRUE),
    ('10000005', 'Gharbi', 'Sami', DATE '1970-06-01', 77, 'O_NEG', 'Sfax', '20000005', TRUE),
    ('10000006', 'Jaziri', 'Ines', DATE '1999-11-11', 58, 'AB_POS', 'Tunis', '20000006', TRUE),
    ('10000007', 'Chaabane', 'Yassine', DATE '1985-02-28', 90, 'B_NEG', 'Sfax', '20000007', FALSE),
    ('10000008', 'Dridi', 'Mariem', DATE '2003-07-15', 55, 'AB_NEG', 'Sfax', '20000008', TRUE);

INSERT INTO ajournement (donneur_id, motif, date_debut, date_fin)
SELECT id, 'INFECTION', CURRENT_DATE - 2, CURRENT_DATE + 14
FROM donneur
WHERE cin = '10000003';

INSERT INTO creneau (centre_id, debut, fin, capacite)
SELECT c.id,
       d::timestamp + TIME '09:00',
       d::timestamp + TIME '10:00',
       4
FROM centre c
CROSS JOIN generate_series(CURRENT_DATE, CURRENT_DATE + 6, INTERVAL '1 day') AS d
WHERE c.nom IN ('CRTS Sfax', 'CHU Habib Bourguiba', 'CHU Hedi Chaker', 'Collecte Mobile');
