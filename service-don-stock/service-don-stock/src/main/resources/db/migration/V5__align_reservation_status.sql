-- =============================================================
-- V5 – Align reservation status with the protobuf contract
-- =============================================================
-- A_CONFIRMER was the internal name used by the first draft.  The published
-- contract exposes EN_ATTENTE_CONFIRMATION; new rows already use that value.
-- Migrate rows created before this change so ConsulterReservation no longer
-- answers STATUT_RESERVATION_UNSPECIFIED for them.
-- =============================================================

UPDATE reservation
   SET statut = 'EN_ATTENTE_CONFIRMATION', updated_at = NOW()
 WHERE statut = 'A_CONFIRMER';
