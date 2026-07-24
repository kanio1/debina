# E1 CreDtTm lineage correction proposal

Status: `AI_DRAFT` — design only; **no Flyway migration in this run**

Review state: `NOT_REVIEWED`

## Problem statement

`GrpHdr/CreDtTm` is the ISO source message creation timestamp. Current
implementation does not parse it:

- `Pain001CanonicalMapper` does not read `GrpHdr/CreDtTm`.
- `Pain001LineageRecorder` writes `clockPort` / `recordedAt` into
  `iso.iso_messages.cre_dt_tm`.
- `ingress.raw_inbound_messages.received_at` already stores receive time.

This conflates three distinct times under a source-named column:

| Semantic | Intended source | Current persistence |
|---|---|---|
| Source message creation | `GrpHdr/CreDtTm` | **not parsed** |
| Receive time | HTTP/archive instant | `ingress.raw_inbound_messages.received_at` |
| Record/processing time | lineage write instant | incorrectly stored as `iso.iso_messages.cre_dt_tm` |

Downstream read model `IsoPaymentEvidenceReadModel` uses
`COALESCE(im.cre_dt_tm, im.created_at)` — any correction must preserve
compatibility during expand-contract.

## Recommended correction (expand → backfill → contract)

### Phase 1 — expand (new forward migration `iso/V22__...`)

Add columns to `iso.iso_messages`:

- `source_message_created_at timestamptz(3) null` — parsed `GrpHdr/CreDtTm`
- `recorded_at timestamptz(3) null` — explicit processing/lineage record instant

Do **not** drop or rename `cre_dt_tm` in phase 1.

### Phase 2 — application change (post-approval implementation)

1. Parse `GrpHdr/CreDtTm` in mapper or dedicated lineage parser.
2. Write `source_message_created_at` from XML.
3. Write `recorded_at` from `clockPort` at lineage record time.
4. Stop writing receive time into `cre_dt_tm`.

### Phase 3 — backfill policy

| Row class | `source_message_created_at` | `recorded_at` | `cre_dt_tm` legacy |
|---|---|---|---|
| New pain.001 rows after deploy | from XML | from clockPort | copy `recorded_at` for transition or leave null |
| Historical rows | `unknown` / null | existing `cre_dt_tm` if it reflected record time | unchanged |
| Rows with no XML re-parse | null + audit flag | best-effort from `cre_dt_tm` | unchanged |

Unknown historical source `CreDtTm` values remain null; do not fabricate.

### Phase 4 — contract (later migration)

After read models and APIs migrate:

- Deprecate `cre_dt_tm` in documentation.
- Optional rename migration to `legacy_recorded_at` or drop after consumer proof.

## API / read-model compatibility

- GraphQL and REST reads expose `sourceMessageCreatedAt`, `receivedAt`,
  `recordedAt` as separate optional fields.
- During transition, reads may fall back `recordedAt := COALESCE(recorded_at, cre_dt_tm)`.
- Ordering for operational lists must not silently switch to source creation time
  without product approval.

## Audit and ordering consequences

- Evidence timelines must show receive → verify → record → map ordering.
- Source `CreDtTm` is informational for customer message origin; it must not
  replace receive time for SLA or transport metrics.

## Focused tests (implementation phase)

- Unit: parser extracts `CreDtTm` with and without timezone offset.
- Integration: lineage row has three distinct timestamps on intentional fixture
  with delayed `CreDtTm` vs receive.
- Negative: missing `CreDtTm` fails profile validation once admitted.
- Read model: COALESCE compatibility during transition.

## Flyway impact checklist

| Item | Value |
|---|---|
| Schema owner | iso |
| Tables | iso.iso_messages |
| Current highest iso migration | V21 |
| Applied in prod | unknown — forward-only regardless |
| Lock risk | low (nullable add) |
| Backward compatibility | preserved via nullable columns |
| RLS/grants | iso writer only; no cross-schema write |
| Fresh DB verification | Testcontainers from empty |
| Upgrade verification | Testcontainers from V21 state |

## Authorization

This proposal does not authorize migration execution. Requires
`DATABASE_MAPPING_REVIEW` and owner approval after human review gate.
