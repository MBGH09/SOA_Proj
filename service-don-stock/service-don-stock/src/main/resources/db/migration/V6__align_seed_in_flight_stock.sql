-- =============================================================
-- V6 – Align seeded in-flight stock with the simulator fixtures
-- =============================================================
-- V4 seeds two central orders (dem-hbs-003, dem-hk-002) in
-- APPROVISIONNEMENT_COMMANDE and two donations (don-seed-004,
-- don-seed-005) in QUALIFICATION_EN_COURS.  All four are already older
-- than the simulator delays (30s / 20s), so DeliverySimulatorJob and
-- QualificationSimulatorJob process them at startup.
--
-- V2/V3 leave en_transit and en_qualification at zero, so the jobs used
-- to mark the fixtures LIVREE / QUALIFIE while moving no pouch at all.
-- Mirror what verifierEtReserver() and enregistrerPrelevement() do at
-- runtime:
--   * en_transit += reservation.quantite at the requesting hospital
--   * en_qualification += 1 per component at the collecting centre
--
-- The updates are driven by the seeded rows and guarded by their status,
-- so databases that already ran the simulators do not gain phantom
-- transit/qualification stock that no job will ever consume.
-- =============================================================

-- Central orders placed before startup → pouches are on their way.
UPDATE stock_niveau s
   SET en_transit = s.en_transit + r.quantite
  FROM reservation r
 WHERE r.demande_id IN ('dem-hbs-003', 'dem-hk-002')
   AND r.statut = 'APPROVISIONNEMENT_COMMANDE'
   AND s.lieu_id        = r.lieu_id
   AND s.groupe_sanguin = r.groupe_sanguin
   AND s.composant      = r.composant;

-- Donations declared to CNTS but not yet qualified → one pouch in lab
-- per component (CGR, PLASMA, PLAQUETTES), at the effective stock centre.
UPDATE stock_niveau s
   SET en_qualification = s.en_qualification + 1
  FROM prelevement p
 WHERE p.don_id IN ('don-seed-004', 'don-seed-005')
   AND p.statut = 'QUALIFICATION_EN_COURS'
   AND s.lieu_id        = p.centre_id
   AND s.groupe_sanguin = p.groupe_sanguin
   AND s.composant IN ('CGR', 'PLASMA', 'PLAQUETTES');
