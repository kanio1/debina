-- owner: iso-adapter [MVP] — EPIC-21 Story 21.2, backfill phase. Every payment created through the
-- application since Story 19.1 (JsonDirectLineageRecorder/Pain001LineageRecorder) already gets an
-- iso.payment_iso_identifiers + iso.message_lineage row automatically. A handful of payments predate
-- that (early walking-skeleton smoke rows, EPIC-04/06/08) and have no identifier row at all — this
-- migration backfills exactly those, from their still-present payment.payments.end_to_end_id value,
-- before V18 drops that column. Every backfilled row predates any real XML channel, so
-- source_message_type='JSON_DIRECT' for all of them (matches JsonDirectLineageRecorder's own shape).
-- No conflicts exist to resolve: verified against the real long-running database (zero payments with
-- more than one identifier row) before writing this migration.

WITH orphans AS (
    SELECT p.id AS payment_id, p.end_to_end_id, gen_random_uuid() AS new_iso_message_id
    FROM payment.payments p
    WHERE NOT EXISTS (
        SELECT 1 FROM iso.payment_iso_identifiers pii WHERE pii.payment_id = p.id
    )
),
inserted_messages AS (
    INSERT INTO iso.iso_messages (id, direction, message_type, parse_status)
    SELECT new_iso_message_id, 'INBOUND', 'JSON_DIRECT', 'SKIPPED' FROM orphans
    RETURNING id
),
inserted_identifiers AS (
    INSERT INTO iso.payment_iso_identifiers (payment_id, source_message_type, iso_message_id, end_to_end_id)
    SELECT payment_id, 'JSON_DIRECT', new_iso_message_id, end_to_end_id FROM orphans
    RETURNING payment_id, iso_message_id
)
INSERT INTO iso.message_lineage (lineage_role, iso_message_id, payment_id)
SELECT 'ORIGINAL_INSTRUCTION', iso_message_id, payment_id FROM inserted_identifiers;
