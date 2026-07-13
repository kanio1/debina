# ADR-N7 — `JSON_DIRECT` Pseudo Message-Version

## Status

Frozen

## Context

Blueprint §2.2 promises that the direct-JSON payment submission path records ISO identifiers and lineage exactly like the XML paths ("Direct JSON path skips parse/version but still records identifiers + lineage"). But the DDL as written cannot honor that promise: `iso.payment_iso_identifiers` has `iso_message_id uuid NOT NULL REFERENCES iso.iso_messages(id)` as part of its primary key, and `iso.iso_messages.message_version_id` is itself `NOT NULL REFERENCES iso.iso_message_versions(id)`. A JSON submission has no ISO message and no ISO version to point at — the two NOT NULL constraints make the promised identifiers-and-lineage recording impossible without either weakening the schema (nullable `iso_message_id`, which breaks the correlation model's primary key) or silently skipping lineage for JSON (which breaks the correlation promise and the "every payment is traceable" lineage principle in §4.3b). This sits directly on the Iteration 1 hot path — the JSON submission flow is the very first vertical slice.

## Decision

`[FREEZE]` **`JSON_DIRECT` is a seeded, synthetic ISO message-type/version pair, not a schema exception.** One row is seeded into `iso.iso_message_versions` at `message_type='JSON_DIRECT'`, with a stable `scheme_profile_id`, `validation_profile_code`, and `mapping_profile_code` referring to trivial pass-through profiles. Every JSON submission creates exactly one `iso.iso_messages` row with `direction='INBOUND'`, `message_type='JSON_DIRECT'`, `parse_status='SKIPPED'` (parsing was never needed — the JSON payload is already canonical-shaped), and `raw_message_id` pointing at the archived raw JSON body. Identifiers extracted directly from the JSON body (`endToEndId` etc.) are written into `iso.payment_iso_identifiers` against that `iso_messages` row exactly as any other channel would, and `iso.message_lineage` gets one `ORIGINAL_INSTRUCTION` row. `iso_message_id` stays `NOT NULL` everywhere — no nullable shortcut, no bypass of the lineage model.

```text
POST /api/v1/payments (JSON)
  → raw archive (ingress.raw_inbound_messages)
  → idempotency gate
  → create iso.iso_messages(direction=INBOUND, message_type='JSON_DIRECT', parse_status='SKIPPED', raw_message_id=...)
  → extract identifiers directly from the JSON payload (no XML parse)
  → write iso.payment_iso_identifiers(iso_message_id = the JSON_DIRECT row, end_to_end_id, ...)
  → write iso.message_lineage(lineage_role='ORIGINAL_INSTRUCTION', iso_message_id=..., payment_id=...)
  → create canonical payment (payment.payments)
```

## Consequences

- The correlation model is uniform across every channel: a JSON-submitted payment can be found and correlated (e.g. by a later pacs.002-equivalent) through exactly the same `iso.payment_iso_identifiers` lookup as an XML-submitted one.
- No schema weakening: `iso_message_id NOT NULL` is preserved everywhere; no special-cased nullable column, no parallel identifier table for JSON.
- `JSON_DIRECT` is explicitly `[SYNTHETIC]`/educational — it does not claim to be a real ISO 20022 message type; it exists purely to keep the lineage/correlation model honest for the REST-JSON channel, which is real in this platform (PSP-style JSON submission) even though it has no ISO equivalent.
- Closes R-08 from the blueprint review and B3 from the decision gate; unblocks Iteration 1.

## Alternatives Rejected

- **Make `iso_message_id` nullable on `iso.payment_iso_identifiers`** — rejected: breaks the composite primary key `(payment_id, source_message_type, iso_message_id)` and reopens the exact "ISO identity flattened into business state" bug the v2 patch fixed for the `payments` table.
- **Skip identifier/lineage recording for JSON entirely** — rejected: silently breaks the "every payment is traceable" lineage principle for what will likely be the most common ingress channel in early demos (simple REST JSON), and contradicts §2.2's own stated behaviour.
