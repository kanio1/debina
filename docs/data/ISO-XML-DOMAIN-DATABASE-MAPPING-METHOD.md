# ISO XML → domain → database mapping method

Status: `AI_DRAFT`, `NOT_REVIEWED`
Scope: Phase E research and design only; no production schema or parser is admitted by this document.

## Purpose and invariant

An external XML tree is evidence and a transport contract, not the Debina domain model.
Mapping therefore follows six explicit layers:

```text
external message structure
→ canonical payment concept
→ aggregate/entity/value object candidate
→ immutable technical lineage/evidence
→ module-owned write model
→ source-owned operational read model
```

There is no “one XML element → one database column” rule. A noun in an XSD,
rulebook, or CSM guide is not by itself an aggregate. Every new aggregate candidate
is `AGGREGATE_REVIEW_REQUIRED`; every new schema, public port, or cross-module
transaction is subject to `architecture-evolution-review`.

## Authority and admission gate

Before mapping any field, the mapper owner must record:

1. exact scheme, direction, lifecycle stage, message version and default ISO namespace;
2. exact EPC IG/TVS and, where applicable, CSM/participant profile;
3. reviewed document section, effective date, addendum/errata state and source status;
4. admitted use case and behavioral slice;
5. lossless/lossy decision and the owner of any generated identifier.

Unknown versions, sections, effective dates, participant-only rules, or conflicting
profiles are `SOURCE_BLOCKED`. Project choices are labelled `PROJECT_SIMULATION`,
`PROJECT_POLICY`, or `[ASSUMPTION]`; they are never promoted to EPC/ISO/CSM claims.

## Mapping workflow

### 1. Preserve the envelope and raw evidence

Archive the exact received bytes before signature verification or parsing. The envelope
has a stable evidence ID, tenant, channel identity, uploader/system identity, received
time, content type, encoding/compression state, checksum, correlation/causation IDs,
idempotency key, signature/decryption/validation/quarantine states and audit lineage.

The raw payload is immutable and append-only. A resend creates new receipt evidence;
domain idempotency decides whether it creates new business state. Raw payload access is
privileged, audited, masked by default and excluded from logs and ordinary GraphQL/UI
models.

### 2. Verify provenance before semantic trust

When a channel requires cryptographic provenance, verify the signature over the exact
archived bytes before parsing. Store signature bytes only in the signature-owned
evidence boundary; store algorithm, key/certificate reference, covered hash, verdict,
reason and timestamps. Never design custom cryptography. Certificate trust,
revocation, rotation, mTLS and non-repudiation semantics come from the selected
channel profile and security review.

### 3. Detect and validate message structure

The intake layer performs bounded, deterministic checks:

- content-size, decompression and transaction-count limits;
- encoding, XML well-formedness and exact namespace/version detection;
- DTD/XXE/entity-expansion prohibition, depth and text-length limits;
- ISO XSD validation followed by EPC TVS/profile validation;
- optional Business Application Header validation only when the rail profile requires it.

Streaming versus DOM is decided from measured file size, count, memory and error
reporting quality. The E1 single-transaction pilot may retain hardened DOM only behind
explicit size/depth limits. E3 must run `QUALITY_EXPERIMENT_REQUIRED` before choosing
a multi-instruction parser.

### 4. Apply scheme/business validation

Schema validity does not establish business eligibility. A versioned rule profile
validates group header, `NbOfTxs`, control sum, identifiers, currency/amount,
requested execution date, parties/accounts/agents, IBAN/BIC, addresses, charges,
purpose/category purpose, remittance and scheme restrictions.

VoP, sanctions, fraud, approval, routing, liquidity and service availability are
separate capabilities/gates with separate evidence. Partial-file acceptance remains
`DECISION_BLOCKED` until a cited source/channel rule and product decision exist.

### 5. Map to canonical concepts without collapsing them

The mapping record distinguishes:

- business payment order;
- customer instruction and payment transaction;
- file, group, batch, envelope and submission;
- ISO message and rail instruction;
- clearing item, settlement result, receipt and delivery;
- reject, return, recall, request for recall, cancellation and case;
- immutable evidence and audit event.

Identifiers keep their owner and level: `BizMsgIdr`, `MsgId`, `PmtInfId`, `InstrId`,
`EndToEndId`, `TxId`, `UETR` and `Orgnl*` values are never aliases. Correlation is
an explicit lineage relation, not a copied “universal payment ID”.

### 6. Admit storage through module ownership

For every proposed column record the writer module, schema, tenant key, RLS policy,
constraint, uniqueness scope, index purpose, retention and masking. Preserve:

- one writer per schema;
- RLS-only tenant isolation with fail-closed tenant context;
- append-only evidence/audit;
- LedgerPort-only reserve/post/release/reverse;
- five status axes: transport/receipt, ISO processing, business lifecycle,
  settlement/finality and accounting;
- outbox/inbox for asynchronous delivery and idempotent consumers.

`jsonb`, PostgreSQL `xml`, partitioning and generated columns require a measured query,
retention or migration need. PostgreSQL 18 is the current baseline; PostgreSQL 19
features are only `FUTURE_DATABASE_OPTION`.

### 7. Design read models after commands

REST/gRPC own commands. Query-only GraphQL and BFF/UI consume source-owned read ports;
they do not create a second ownership model. Read models expose redacted identifiers,
validation summaries, timeline/status axes and evidence metadata, never unrestricted raw
XML or secret/signature material.

## Required decision record

Each mapped element uses
[ISO-XML-DOMAIN-DATABASE-MAPPING-TEMPLATE.yaml](ISO-XML-DOMAIN-DATABASE-MAPPING-TEMPLATE.yaml).
The record is incomplete unless all keys are present. `TBD`, `SOURCE_BLOCKED`,
`DECISION_BLOCKED` and `NOT_APPLICABLE` are honest values; empty ambiguity is not.

Additional message-level decisions:

| Decision | E1 disposition | Review gate |
|---|---|---|
| Full original XML | Keep exact bytes as immutable restricted evidence | Security + legal + database |
| Raw format | `bytea` is current implementation; object storage is a future option after size/retention experiment | Database |
| Hash/checksum | SHA-256 receipt hash retained; algorithm versioned | Security |
| Signature/certificate lineage | Existing synthetic Ed25519 event is project policy; channel profile is unresolved | Security |
| Parser | Bounded hardened DOM for E1 only; E3 experiment decides streaming | QA + architecture |
| File/count/control sum | Validate against exact message/profile; E1 admits one transaction | ISO |
| Identifiers | Separate typed lineage fields and uniqueness scopes | ISO + database |
| Duplicate/idempotency | Raw receipt never deduplicates; business command is idempotent | Architecture + database |
| Amount/currency/date | Exact decimal/type/range/profile rules; no float | Payments + database |
| Parties/accounts/addresses | Canonical value objects plus restricted evidence; minimize/mask | Payments + privacy |
| Remittance/purpose/charges | Preserve losslessly or record explicit loss | Payments |
| Supplementary data | Quarantine/preserve; do not silently drop | ISO |
| Partial failure | `DECISION_BLOCKED` for E3 | Product + payments |
| Retry/quarantine | New receipt evidence and idempotent command; operator actions audited | Operations |
| Retention | `LEGAL_REVIEW_REQUIRED`; FTR3 five-year record requirement is not the whole policy | Legal/privacy |

## Verification allocation

Primary verification belongs at the narrowest risk-owning layer:

- source/XSD/TVS: version, namespace, cardinality and code-set fixtures;
- unit/property: normalization, identifiers, decimal boundaries and control sums;
- database: ownership, grants, RLS, constraints, idempotency and append-only behavior;
- integration: archive → signature → parse → map → persist transaction boundaries;
- component/Dagger: accepted, rejected and duplicate journey with runtime evidence;
- Playwright: one minimal signed-upload/result smoke, not backend-validation duplication.

No mapping becomes implementation-ready until `ISO_MESSAGE_REVIEW`,
`DATABASE_MAPPING_REVIEW`, `SECURITY_REVIEW`, `ARCHITECTURE_REVIEW`,
`PAYMENTS_DOMAIN_REVIEW` and `QA_REVIEW` leave recorded approval evidence.
