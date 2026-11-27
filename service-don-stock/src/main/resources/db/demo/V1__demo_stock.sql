CREATE TABLE IF NOT EXISTS lieu (
    id VARCHAR(20) PRIMARY KEY,
    nom VARCHAR(200) NOT NULL,
    type VARCHAR(40) NOT NULL,
    stock_local BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS niveau_stock (
    lieu_id VARCHAR(20) NOT NULL REFERENCES lieu (id),
    groupe_sanguin VARCHAR(10) NOT NULL,
    composant VARCHAR(20) NOT NULL,
    quantite INTEGER NOT NULL,
    PRIMARY KEY (lieu_id, groupe_sanguin, composant)
);

INSERT INTO lieu (id, nom, type, stock_local) VALUES
    ('CNTS-TN', 'CNTS Tunis', 'CNTS', FALSE),
    ('CRTS-SFX', 'CRTS Sfax', 'CRTS', FALSE),
    ('HOP-SFX-01', 'CHU Habib Bourguiba', 'BANQUE_HOPITAL', TRUE),
    ('HOP-SFX-02', 'CHU Hedi Chaker', 'BANQUE_HOPITAL', TRUE),
    ('CLI-SFX-01', 'Clinique exemple Sfax', 'SANS_BANQUE', FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO niveau_stock (lieu_id, groupe_sanguin, composant, quantite) VALUES
    ('HOP-SFX-01', 'O_POS', 'CGR', 12),
    ('HOP-SFX-01', 'O_NEG', 'CGR', 1),
    ('HOP-SFX-01', 'A_POS', 'PLASMA', 6),
    ('HOP-SFX-02', 'B_POS', 'CGR', 4),
    ('HOP-SFX-02', 'AB_NEG', 'PLAQUETTES', 0),
    ('CRTS-SFX', 'O_POS', 'CGR', 40),
    ('CRTS-SFX', 'O_NEG', 'CGR', 1),
    ('CNTS-TN', 'O_POS', 'CGR', 80),
    ('CNTS-TN', 'A_NEG', 'PLASMA', 15)
ON CONFLICT (lieu_id, groupe_sanguin, composant) DO NOTHING;
