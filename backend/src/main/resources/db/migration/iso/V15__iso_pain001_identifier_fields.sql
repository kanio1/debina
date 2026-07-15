-- owner: iso-adapter [MVP] — sepa-nexus-message-flow-and-data-blueprint.md §4.3c (frozen richer-form
-- DDL) EPIC-19 Story 19.4. V11's own comment deferred these columns "until real XML channels
-- (Story 19.4) are built" — this is that story. Additive/nullable only: JSON_DIRECT rows (Story
-- 19.1, JsonDirectLineageRecorder) keep writing exactly the columns they already write; no
-- existing INSERT breaks. tx_id/orgnl_* from the full §4.3c DDL are intentionally not added here —
-- they belong to pacs.008/pacs.002 correlation (EPIC-26/27), not pain.001 mapping.

ALTER TABLE iso.iso_messages
    ADD COLUMN msg_id text,
    ADD COLUMN cre_dt_tm timestamptz(3);

ALTER TABLE iso.payment_iso_identifiers
    ADD COLUMN msg_id text,
    ADD COLUMN pmt_inf_id text,
    ADD COLUMN instr_id text,
    ADD COLUMN uetr text;
