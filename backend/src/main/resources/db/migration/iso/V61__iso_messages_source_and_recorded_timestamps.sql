-- owner: iso-adapter — E1 CreDtTm expand-contract (UC-SCT-002 / E1-CRE-DT-TM-CORRECTION-PROPOSAL)
-- Separates source GrpHdr/CreDtTm from Debina record/processing time. Legacy cre_dt_tm is retained
-- for transition reads; do not drop or rename in this migration.

ALTER TABLE iso.iso_messages
    ADD COLUMN source_message_created_at timestamptz(3),
    ADD COLUMN recorded_at timestamptz(3);

-- Best-effort backfill: historical rows stored receive/record time in cre_dt_tm.
UPDATE iso.iso_messages
SET recorded_at = cre_dt_tm
WHERE recorded_at IS NULL AND cre_dt_tm IS NOT NULL;
