-- Jeu de donnees de demonstration pour service-demandes.
-- Script idempotent : les UUID fixes evitent les doublons lors d'une seconde execution.

INSERT INTO demande (
    id, hopital_id, hopital_nom, patient_ref, motif, groupe_sanguin,
    composant, quantite_poches, urgence, statut, reference_approvisionnement,
    compensation_demandee, compensation_recue, date_creation
) VALUES
(
    '10000000-0000-0000-0000-000000000001',
    'HOP-TUN-01', 'CHU Habib Bourguiba Sfax', 'PAT-DEMO-001',
    'CHIRURGIE', 'O_NEG', 'CGR', 4, 'CRITIQUE', 'SATISFAITE', 'RES-DEMO-001',
    2, 1, CURRENT_TIMESTAMP - INTERVAL '5 days'
),
(
    '10000000-0000-0000-0000-000000000002',
    'HOP-TUN-02', 'CHU Charles Nicolle Tunis', 'PAT-DEMO-002',
    'URGENCE', 'A_POS', 'PLASMA', 3, 'HAUTE', 'APPROVISIONNEMENT_EN_COURS', 'APP-DEMO-002',
    1, 0, CURRENT_TIMESTAMP - INTERVAL '2 days'
),
(
    '10000000-0000-0000-0000-000000000003',
    'HOP-TUN-03', 'Hopital Farhat Hached Sousse', 'PAT-DEMO-003',
    'TRAITEMENT', 'B_NEG', 'PLAQUETTES', 2, 'NORMALE', 'EN_PENURIE', NULL,
    1, 0, CURRENT_TIMESTAMP - INTERVAL '1 day'
),
(
    '10000000-0000-0000-0000-000000000004',
    'HOP-TUN-01', 'CHU Habib Bourguiba Sfax', 'PAT-DEMO-004',
    'CHIRURGIE', 'AB_POS', 'CGR', 2, 'HAUTE', 'EN_ATTENTE', NULL,
    2, 0, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO appel_don (
    id, demande_id, groupe_sanguin, composant, centre_cible_id,
    statut, date_ouverture, date_fermeture
) VALUES
(
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000003',
    'B_NEG', 'PLAQUETTES', 'CENTRE-SOUSSE-01',
    'OUVERT', CURRENT_TIMESTAMP - INTERVAL '1 day', NULL
),
(
    '20000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000001',
    'O_NEG', 'CGR', 'CENTRE-SFAX-01',
    'FERME', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '4 days'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO compensation (
    id, demande_id, numero_certificat, date_enregistrement
) VALUES
(
    '30000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    'CERT-DEMO-001', CURRENT_TIMESTAMP - INTERVAL '4 days'
),
(
    '30000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000003',
    'CERT-DEMO-002', CURRENT_TIMESTAMP - INTERVAL '12 hours'
)
ON CONFLICT (id) DO NOTHING;
