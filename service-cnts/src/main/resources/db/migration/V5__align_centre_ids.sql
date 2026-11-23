-- =============================================================================
-- V5 : Align stock centre ids with the XSD examples used by S5-06
-- =============================================================================
-- demanderApprovisionnement looks up CRTS-<REGION> then CNTS-TUNIS
-- (cnts.xsd: CRTS-SFAX or CNTS-TUNIS). V2 seeded CNTS-TN and CRTS-SFX,
-- so a real order never found a row. Fresh databases get the new ids from
-- the updated V2; this migration fixes databases that already applied V2.
-- =============================================================================

UPDATE stock_central SET centre_id = 'CNTS-TUNIS' WHERE centre_id = 'CNTS-TN';
UPDATE stock_central SET centre_id = 'CRTS-SFAX'  WHERE centre_id = 'CRTS-SFX';
