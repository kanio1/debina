# SEPA payment test allocation matrix

Status: `AI_DRAFT`, `NOT_REVIEWED`
Rule: one primary layer owns each behavior. A secondary layer is allowed only when it
proves a different integration or runtime risk.

| Behavior | Primary test layer | Secondary test layer | Why secondary is not duplicate | Runtime verify |
|---|---|---|---|---|
| Source manifest has version/effective date/section/addenda | Static/source | None | — | Governance validator |
| Namespace and message version detection | Contract/XML fixture | Integration | Integration proves HTTP bytes reach the detector safely | Submit wrong namespace; deterministic problem code |
| ISO XSD validity | Static/schema | Integration | Integration proves the deployed schema bundle is selected | Accepted/rejected fixture evidence |
| EPC TVS/profile validity | Contract/XML fixture | Component | Component proves the configured profile, not every rule again | One accepted and one profile-rejected fixture |
| XML well-formedness, XXE and entity expansion | Unit/security fixture | Component | Component proves archive-before-reject and no payment mutation | Dagger rejected-message evidence |
| Size/depth/text/count limits | Property/boundary | Component | Component checks HTTP/runtime enforcement and stable error | Oversize smoke without raw payload logging |
| Exact raw-byte archive and SHA-256 | Database integration | Dagger | Dagger proves composed persistence survives later failure | Query redacted receipt/hash evidence |
| Signature-before-parse ordering | Integration | Dagger | Dagger observes no parse/payment side effect after bad signature | Invalid-signature journey |
| Signature/certificate trust decision | Security integration | Dagger | Dagger proves deployed trust material wiring/rotation behavior | Valid/expired/untrusted cases by cohort |
| `MsgId`, `InstrId`, `EndToEndId`, `TxId`, `UETR` separation | Unit/mapping | Database | Database proves distinct nullable/unique scopes and lineage | Detail API exposes redacted typed identifiers |
| Identifier character and length boundaries | Property-based | None | — | Unit suite evidence |
| Amount/currency/IBAN/BIC normalization | Property-based | Database | Database proves exact numeric/constraint representation | Focused integration evidence |
| `NbOfTxs` and control sum | Property-based | Component | Component proves file-level rejection policy and audit | E3 only |
| Duplicate receipt vs idempotent business command | Database integration | Dagger | Dagger proves retry through real API without duplicate payment/outbox | Two submissions, two receipts, one business outcome |
| Canonical mapping completeness/lossiness | Static mapping completeness | Unit | Unit proves transformations for admitted fields | Mapping report attached to E1 proof |
| Payment, instruction, message and file/group relations | Database integration | Component | Component proves query journey over persisted relations | E1 single; E3 multi-instruction |
| Maker-checker segregation and self-approval denial | Security integration | Dagger | Dagger proves Keycloak token/REST/audit/RLS composition | Submitter denied; checker succeeds |
| Approval concurrency and command idempotency | Database integration | Component | Component proves externally visible conflict result | Two concurrent decisions, one outcome |
| Tenant/branch isolation and empty-GUC fail closed | Database | Dagger | Dagger proves deployed role/session propagation | Cross-tenant read/write denied |
| REST command contract | OpenAPI/contract | Component | Component proves authentication and persistence wiring | Signed submission response/location |
| Query-only GraphQL and source-owned reads | Architecture/contract | Dagger | Dagger proves production introspection policy and data visibility | Query succeeds; mutation absent |
| BFF session, CSRF/origin and body limits | BFF unit/route | Playwright smoke | Browser smoke proves cookie/session integration only | Login and signed upload |
| Kafka envelope/topic/schema and partition key | Contract | Integration | Integration proves actual producer/consumer compatibility | Topic record with schema/correlation evidence |
| Outbox/inbox redelivery and out-of-order status | Integration | Dagger | Dagger proves composed broker/database recovery | Duplicate/out-of-order proof in E6 |
| Five status axes and legal transitions | Unit/state table | Integration | Integration proves persisted evidence cannot collapse axes | Detail timeline shows distinct axes |
| Rail admission, receipt and finality | Rail contract | Dagger | Dagger proves profile-specific adapter and settlement evidence | E4/E5 only; never inferred from send |
| Reconciliation totals/unmatched/late items | Database integration | Component | Component proves operator workflow and evidence export | E7 mismatch journey |
| Log redaction and restricted evidence | Security/static log assertion | Dagger | Dagger scans composed failure artifacts and audit authorization | No XML/account/name/address/secret |
| Large files/many transactions | Quality experiment | Dagger benchmark gate | Dagger provides reproducible environment after threshold decision | E3 experiment; no arbitrary target |
| CSM/DB/Keycloak outage and retry/recovery | Integration fault injection | Dagger | Dagger proves composed availability/fail-closed behavior | Cohort-specific recovery evidence |
| Certificate expiry alert, Kafka lag, DLQ and source freshness | Observability integration | Dagger | Dagger proves metrics/alerts are wired | Scrape asserted metric/health state |

## Cohort E1 allocation

Primary E1 suite:

1. official `pain.001.001.09` ISO XSD plus EPC 2025 SCT C2PSP TVS fixtures;
2. focused unit/property tests for the admitted mapping and boundaries;
3. Testcontainers-first PostgreSQL tests for raw evidence, lineage, RLS, grants,
   idempotency and append-only behavior;
4. Spring integration for archive → verify → hardened parse → XSD/TVS → map → persist;
5. REST/BFF contract tests and query-only GraphQL drift checks;
6. one composed Dagger accepted journey, one invalid-signature rejection and one
   idempotent replay proof.

Playwright remains a minimal smoke:

1. sign in as a user with the submission role;
2. upload one signed `pain.001`;
3. see validation outcome or payment detail with typed identifiers;
4. maker-checker only if the reviewed E1 contract keeps approval in scope.

No browser matrix, visual regression, broad E2E, or repetition of backend validation
cases is admitted in E1.

## Quality-scenario ownership

| Scenario | Existing/new outcome |
|---|---|
| Large file and many transactions | `QUALITY_EXPERIMENT_REQUIRED` in E3 |
| Low-latency instant payment | `QUALITY_EXPERIMENT_REQUIRED` in E5; rail/source threshold first |
| Duplicate/replay and out-of-order Kafka | Existing idempotency/ordering scenarios extended in E1/E6 |
| Unavailable CSM, database failure, retry, partial failure | Adapter/fault experiments in E4–E7 |
| Keycloak outage | Fail-closed composed security scenario in E1/E2 |
| Certificate expiry/invalid signature | New provenance quality scenario in E1 |
| Corrupted XML | Existing XML-hardening scenario expanded with size/depth/TVS |
| Tenant isolation/restricted evidence/log redaction | Existing RLS/audit scenarios expanded in E1/E2 |
| Recovery/reconciliation mismatch | E7 component and Dagger evidence |

Numeric TPS, latency, file-size and transaction-count thresholds remain
`QUALITY_EXPERIMENT_REQUIRED` unless an official profile or explicit project decision
supplies them.
