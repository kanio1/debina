# Debina Phase E — controlled backlog migration and end-to-end SEPA payment research program

Status: `AI_DRAFT`, `NOT_REVIEWED`
Planning baseline: `8b86e254ffa29a436385c8c21974751b4b2b5535`, branch `rebase/enterprise-evolution`, 2026-07-24
Session mode: `PREPARE_REVIEW`; no production implementation or canonical story migration
Completion authority: human review plus the cohort exit gates defined below

## 1. Executive summary

Phase E converts a broad legacy backlog into small, source-qualified, vertically
testable cohorts. It closes the chain:

```text
official source
→ atomic claim
→ business rule
→ business process
→ use case
→ flow
→ behavioral slice
→ architecture realization
→ epic/story
→ full-stack implementation
→ allocated tests
→ runtime proof
→ planning reconciliation
```

The program does not claim that Debina is a bank, CSM, settlement system, certified
payment hub or compliant product. It does not reopen Phase D, Use-Case 2.0 or the
constitutional ADRs. It does not authorize production implementation, mass-migrate
304 stories, infer participant-only rules, make GraphQL a command/domain owner or
derive finality from a sent message.

The recommended first delivery cohort is a six-record migration around
`UC-SCT-002 — submit signed pain.001`. It uses existing implementation as evidence
but treats the current signed endpoint as `PARTIALLY_IMPLEMENTED`: ISO XSD/EPC TVS
validation, the channel/signature profile, reviewed resource limits and a user journey
remain open.

## 2. Baseline

### 2.1 Repository and warning baseline

| Item | Baseline |
|---|---|
| HEAD | `8b86e254ffa29a436385c8c21974751b4b2b5535` |
| Branch | `rebase/enterprise-evolution` |
| Upstream | `origin/rebase/phase-b-product-domain-architecture` |
| Ahead / behind at preflight | `0 / 0` |
| Epics | 79 |
| Stories | 304 |
| Use-case traceability warnings | 296 legacy warnings |
| Planning-semantic warnings | 69 legacy warnings |
| Phase D | complete and runtime-proven; not reopened |
| Phase E | E0 baseline complete; E1 integrity closure prepared for human review |
| Protected Modulith javadoc SHA-256 | `47b1b89f63804b4062cd6abe9242a7d56b2212636de95a64784d53723c03e054` |

No warning is removed by this planning session. No existing story status or capability
claim is changed without human review and runtime evidence.

The E1 review pack is canonical for pending decisions:
`planning/reviews/phase-e/e1/README.md`. Its approval gate is false, all queues
remain `NOT_REVIEWED`, and canonical migration remains forbidden.

### 2.2 Actual stack versions

| Area | Repository version | Phase E implication |
|---|---|---|
| JDK | 25; runtime 25.0.3 | use current project toolchain, not remembered versions |
| Spring Boot / Framework | 4.1.0 / 7.0.8 | preserve current dependency management |
| Spring Modulith | 2.1.0 | module admission and architecture tests remain binding |
| PostgreSQL | 18 | no PostgreSQL 19-dependent design |
| Flyway / PostgreSQL JDBC | 12.11.0 / 42.7.13 | forward-only, Testcontainers-first migrations |
| Spring Kafka / Kafka client | 4.1.0 / 4.2.1 | contracts remain project/source qualified |
| Keycloak | 26.6.4 | realm roles are project authorization, not scheme rules |
| Next.js / React | 16.2.10 / 19.2.7 | BFF handles session and command forwarding |
| TypeScript / Node | 6.0.3 / 24.18.0 | pin remains repository authority |
| Package manager | pnpm 10.33.0; local Corepack 10.33.4 | record drift; never substitute npm |
| Spring GraphQL / GraphQL Java | 2.0.4 / 25.0 | query-only, source-owned reads under ADR-N17 |
| Frontend GraphQL | `graphql` 16.11.x | generated/allowlisted contract must stay drift-checked |
| Playwright | 1.60.0 | minimal named smoke only under ADR-N16 |
| Testcontainers | 2.0.5 | authoritative integration/database environment |
| Dagger | CLI 0.21.4 | composed local acceptance; Engine socket unavailable in this session |

`apache/kafka:latest` in Compose differs from the Dagger Kafka 4.1.1 image and is a
recorded toolchain gap, not a Phase E feature change.

## 3. Source authority and evidence discipline

The binding hierarchy is:

```text
EU law / EUR-Lex
→ European Commission / EBA / ECB
→ EPC scheme rulebook
→ EPC implementation guidelines
→ EPC addenda / clarification / guidance
→ ISO 20022 catalogue, repository and message definitions
→ official CSM documentation
→ official central-bank material
→ public participant/bank guide
→ accepted Debina ADR
→ use case and business rule
→ epic/story
→ implementation
```

Search results and implementation are discovery aids, never claim evidence. Public bank
formats are `SECONDARY_IMPLEMENTATION_HINT`. Restricted participant documents are
metadata-only and become `PARTICIPANT_DOCUMENTATION_REQUIRED`; no confidential behavior
is reconstructed. Every AI interpretation, rule, use case, architecture candidate and
story remains `AI_DRAFT`, `NOT_REVIEWED`.

The source manifests, exact sections, effective dates, access dates, supersession and
review state are recorded in:

- `docs/standards/SOURCE-REGISTRY.yaml`;
- `docs/standards/SOURCE-EVIDENCE-CATALOG.yaml`;
- `docs/standards/SOURCE-CHANGE-REGISTER.yaml`;
- `docs/standards/SOURCE-GAP-REGISTER.yaml`.

## 4. Research inventory

### 4.1 Regulations matrix

| Authority | Current state checked | Phase E classification | Planning consequence |
|---|---|---|---|
| Regulation (EU) 260/2012 | consolidated text current to 2024-04-08 | `DIRECT_PRODUCT_REQUIREMENT`, `LEGAL_REVIEW_REQUIRED` | SCT/SDD technical requirements are applicability-scoped |
| Regulation (EU) 2024/886 | published 2024-03-19; in force 2024-04-08 | `DIRECT_PRODUCT_REQUIREMENT`, `REPORTING_REQUIREMENT`, `LEGAL_REVIEW_REQUIRED` | instant, VoP, sanctions and reporting trains stay separate |
| PSD2 / Directive 2015/2366 | consolidated 2025-01-17 | `SECURITY_REQUIREMENT`, `OPERATIONAL_REQUIREMENT`, `LEGAL_REVIEW_REQUIRED` | channel/authentication applicability review |
| PSD3 and Payment Services Regulation | April 2026 Council final compromise; no verified OJ final act | `LEGAL_REVIEW_REQUIRED` | do not implement proposal text as settled law |
| Delegated Regulation 2018/389 (SCA/CSC) | consolidated 2023-09-12 | `SECURITY_REQUIREMENT`, `LEGAL_REVIEW_REQUIRED` | channel-specific SCA/secure communication review |
| Regulation (EU) 2023/1113 | in force; transfer-information and record rules reviewed | `DIRECT_PRODUCT_REQUIREMENT`, `LEGAL_REVIEW_REQUIRED` | evidence/data lineage and five-year rule need controller/legal mapping |
| AMLR 2024/1624 and AML/CFT framework | AMLR applies from 2027-07-10 | `INDIRECT_PLATFORM_REQUIREMENT`, `LEGAL_REVIEW_REQUIRED` | fraud/AML boundary, no invented screening engine |
| GDPR | Articles 5 and 32 reviewed | `SECURITY_REQUIREMENT`, `OPERATIONAL_REQUIREMENT` | minimisation, masking, retention and access control |
| DORA 2022/2554 | applicable; sectoral lex-specialis relationship reviewed | `OPERATIONAL_REQUIREMENT`, `LEGAL_REVIEW_REQUIRED` | resilience, incident and evidence work only where applicable |
| NIS2 2022/2555 | transposition/applicability dates reviewed | `INDIRECT_PLATFORM_REQUIREMENT`, `LEGAL_REVIEW_REQUIRED` | avoid double-counting where DORA is lex specialis |
| EBA instant-payment reporting ITS | final draft 2025-02-04; framework 4.2 schedule published | `REPORTING_REQUIREMENT`, `LEGAL_REVIEW_REQUIRED` | Commission adoption/applicability is a source gap |

This matrix is issue spotting for a synthetic research platform, not legal advice or a
compliance determination.

### 4.2 EPC schemes and guidance

The five public 2025 payment-scheme rulebooks are version 1.1, effective
2025-10-05 and current through 2027-11-21. Their version 1.0 predecessors are historical
comparison material. The confirmed 1.1 change moves the final unstructured-address date
from 2026-11-22 to 2026-11-15.

| Scheme/capability | Phase E state | Reason |
|---|---|---|
| SCT | `PARTIALLY_SUPPORTED` | single instruction and signed pain.001 primitives exist; full EPC profile and rail delivery do not |
| SCT Inst | `PLANNED` | generic sources available; TIPS/RT1/STET participant profiles and runtime semantics gated |
| SDD Core | `OUT_OF_SCOPE` for current cohorts | source inventoried; separate scope decision E8 |
| SDD B2B | `OUT_OF_SCOPE` for current cohorts | separate mandate/participant/business review |
| OCT Inst | `OUT_OF_SCOPE` for current cohorts | no automatic feature train |
| Verification of Payee | `PLANNED` as separate capability | v1.1 effective 2026-09-20; not a payment scheme or hidden approval rule |

Current SCT customer-to-PSP guidance uses `pain.001.001.09` and
`pain.002.001.10`; the inter-PSP SCT/SCT Inst profiles use the ISO 20022 2019
message generation. Scheme, implementation-guideline, TVS, address guidance,
clarification and reason-code materials were checked separately.

### 4.3 ISO 20022 discovery

The ISO catalogue and Financial Repository are authoritative for message definitions,
but an ISO “latest” version is not automatically the EPC or rail version. The discovery
matrix covers:

- `pain.*`: initiation and customer status;
- `pacs.*`: inter-PSP transfer, status, return and inquiry;
- `camt.*`: recall, investigation, modification and reporting candidates;
- `admi.*`: technical/system events, admitted only by a rail profile;
- `head.*`: Business Application Header, profile-dependent.

For SCT, currently relevant candidates include `pain.001.001.09`,
`pain.002.001.10`, `pacs.008.001.08`, `pacs.002.001.10`,
`pacs.004.001.09`, `pacs.028.001.03`, `camt.056.001.08`,
`camt.029.001.09`, `camt.027.001.07` and `camt.087.001.06`.
Necessity remains use-case and rail qualified. See
`docs/standards/ISO-20022-MESSAGE-COVERAGE-MATRIX.yaml`.

### 4.4 Clearing and settlement mechanisms

| CSM/service | Publicly supported claim | Restricted/source boundary | Phase E use |
|---|---|---|---|
| TIPS | 24/7/365 settlement in central-bank money; DCA/reachable/instructing-party model; final and irrevocable public service claim | MyStandards and participant connectivity/profile detail | E5 profile only after lawful artifacts |
| TIPS release documentation | R2026.JUN deployed 2026-06-12; public UDFS/UHB/examples index | exact live message profile still evidence-gated | do not implement published future R2026.NOV as current |
| STEP2 SCT/SDD | public validation, clearing, settlement, warehousing, scheduling and reachability overview | detailed service documentation is customer-portal restricted | E4 public model plus participant gate |
| RT1 | prefunded real-time gross settlement; immediate finality in central-bank funds described publicly | service specifications/participant rules restricted | E5 rail profile gate |
| STET SEPA(EU) | public bilateral-net and prefunded payment-capacity overview | participant/local-community operating rules | E4/E5 adapter profile, never common core |
| CORE(FR)/CORE(BE) | local-community public service descriptions | community-specific rules | not generalized |
| T2/CLM/RTGS/ESMIG/CRDM | context for liquidity/connectivity | not a default product scope | only explicit rail dependencies |

Bundesbank format/TVS examples, Banque de France TIPS guidance and Banca d'Italia
TIPS material were reviewed only as `SECONDARY_IMPLEMENTATION_HINT`.

### 4.5 Current and historical documents

Current: 2025 EPC rulebooks v1.1; 2025 SCT/SCT Inst implementation guidelines;
EPC address guidance v2.1; SCT clarification v4.0; SCT Inst reason codes v7.0;
TIPS R2026.JUN; current public STEP2, RT1 and STET descriptions; current ISO
catalogue.

Historical/transition: 2025 EPC rulebooks v1.0; earlier unstructured-address end date;
prior ISO catalogue repository; published future TIPS R2026.NOV material;
VoP v1.0 (effective 2025-10-05) versus v1.1 (effective 2026-09-20) and the
closed v2.0 consultation. Historical material informs migration but never overrides
the applicable version.

Restricted: STEP2 and RT1 service/participant specifications, TIPS MyStandards
content and any STET participant/local-community rule not public. These are recorded as
metadata only.

## 5. Current-state reconciliation

### 5.1 Implemented path

The inspected path is:

```text
POST /api/v1/iso/pain001
→ archive exact bytes and SHA-256
→ project Ed25519 verification
→ hardened DOM parse
→ one-transaction pain.001.001.09 mapper
→ payment and ISO lineage persistence
→ payment outbox
```

Implemented assets include raw immutable evidence, signature verdicts, ISO identifiers,
payment list/detail/timeline, approval capabilities, audit evidence, query-only GraphQL,
fixed-destination BFF reads and Dagger smoke journeys.

### 5.2 Gaps and contradictions

- The endpoint has no demonstrated ISO XSD or EPC TVS validation.
- No reviewed maximum payload, depth, text or transaction-count profile exists.
- Detached signature headers and Ed25519 are project choices, not EPC requirements.
- The mapper comment that calls `.001.09` the “current ISO SCT release” overstates
  profile authority; the applicable EPC profile is the relevant claim.
- `26.4` says GraphQL is absent although query-only GraphQL and its UI/BFF path exist.
- Some done stories retain unchecked tasks or legacy semantic warnings.
- No signed-upload BFF/UI journey exists.
- No File/Group aggregate, partial-file policy or full file result exists.
- Rail profiles, participant reachability, clearing/settlement contracts and finality
  evidence remain incomplete or source-blocked.

The canonical proposal records are under
`planning/backlog-change-proposals/phase-e/`.

## 6. Target end-to-end lifecycle

```text
channel
→ authenticated/authorized tenant resolution
→ envelope and immutable raw evidence
→ checksum, decryption and signature provenance
→ safe XML parsing and version detection
→ ISO XSD and applicable EPC TVS validation
→ source-qualified business validation
→ canonical payment concepts and lineage mapping
→ owner-schema persistence, RLS and outbox
→ project approval/release
→ route eligibility and explicit no-route
→ customer-to-inter-PSP transformation
→ rail envelope, signing and delivery
→ technical receipt and clearing admission
→ settlement instruction/completion/finality evidence
→ inbound status/R-transaction correlation
→ file/group/payment/accounting reconciliation
→ restricted evidence, audit and role-appropriate UI
```

The following are always distinct: file, envelope, submission, group, business order,
customer instruction, transaction, ISO message, rail instruction, clearing item,
settlement result, receipt, reject, return, recall, case and evidence. Message,
business, transport, ISO and finality status axes never collapse.

Candidate channels are REST, BFF upload, managed file/SFTP, message queue/Kafka,
manual operations, scheduled pickup, CSM adapter and controlled replay. Only REST plus
BFF upload is considered for E1. Every later channel requires actor, authentication,
authorization, tenant, byte/count limits, encryption/signature/certificate policy,
checksum, anti-malware, duplicate/rate/timeout controls, audit, non-repudiation and
operations ownership.

## 7. Cohorts

### E0 — baseline and inventory

- Scope: repository/source/code/frontend warning and capability inventory.
- Entry: Phase D closed and protected hash recorded.
- Exit: source matrices, gap/change registers, reconciliation proposals and this
  program parse and validate.
- Non-goals: status changes, production code, broad story migration.

### E1 — signed `pain.001` pilot

- Stories: `EPIC-31/31.2`, `EPIC-19/19.2`, `EPIC-19/19.4`,
  `EPIC-26/26.3`, `EPIC-26/26.4`, and future proposal `EPIC-24/24.10`.
- Use case/slices: `UC-SCT-002`, `UCS-SCT-002-A`, `UCS-SCT-002-B`.
- Canonical slice meaning: `UCS-SCT-002-A` is the verified-input path through
  archive, verification, source-qualified validation, mapping and accepted-payment
  lineage; `UCS-SCT-002-B` is the failed-signature path with durable safe
  verdict/evidence and no parse or payment mapping.
- Scope: REST/BFF channel, detached-signature decision, raw envelope/evidence,
  safe parsing, XSD/TVS/profile validation, canonical single-payment mapping,
  persistence/lineage, existing approval/status evidence, minimal upload UI and one
  Playwright smoke.
- Entry: Payments, ISO, security, architecture, database, product and QA reviews accept
  the pilot profile; TVS artifacts are lawful and version-pinned; limits are measured
  or declared `QUALITY_EXPERIMENT_REQUIRED`.
- Exit: six records are `ENFORCED`, have zero local legacy warnings, all allocated
  tests pass, Dagger composed acceptance proves the slice, and protected output is
  unchanged.
- Non-goals: multi-instruction files, full STEP2/TIPS/RT1/STET, settlement, recall,
  SDD, broad reporting, browser matrix.

### E2 — implemented-capability reconciliation

- Scope: list/detail, maker-checker, audit/evidence, query-only GraphQL/BFF, Dagger
  smoke and existing ISO lineage.
- Entry: E1 reconciliation contract accepted; implementation evidence re-run or linked.
- Exit: stale prose/status and warning-bearing metadata are reconciled without
  rebuilding features.
- Non-goals: expanding GraphQL command ownership or broad Playwright.

### E3 — file-based multi-instruction

- Candidate: `UC-SCT-003`, only after E1 findings.
- Scope: admitted File/Group/Instruction concepts, `NbOfTxs`, control sum, duplicates,
  large-file strategy, result policy and Files workspace.
- Entry: partial/full rejection policy is source-backed or explicitly
  `DECISION_BLOCKED`; aggregate and parser architecture reviews accepted.
- Exit: field mapping, restart/recovery, transaction isolation and evidence are runtime
  proven.
- Non-goal: choosing Spring Batch merely because the old story names it.

### E4 — SCT routing, clearing and egress

- Scope: eligibility, explicit no-route, separate STEP2/STET profiles,
  pain-to-pacs transformation, deterministic rendering/signing, submission, receipt and
  operational reads.
- Entry: lawful participant or explicitly synthetic adapter contract.
- Exit: no CSM behavior leaks into common core; delivery is not finality.

### E5 — SCT Inst

- Scope: generic eligibility plus separate TIPS/RT1/STET profiles, time/timeout,
  liquidity/pre-funding, finality, beneficiary availability and status.
- Entry: applicable EPC and rail profile versions, human domain/legal review and
  latency experiment.
- Exit: LedgerPort-only movement and explicit finality evidence are proven under
  concurrency and failure.

### E6 — statuses and R-transactions

- Scope: technical/business rejection, status report, return, recall/request/response,
  cancellation, inquiry, timeout, duplicate/out-of-order/unknown correlation and case.
- Exit: five axes remain separate; unknown evidence cannot mutate finality.

### E7 — settlement and reconciliation

- Scope: payment/file/group reconciliation, clearing/settlement/transport reports,
  totals/control sums, unmatched/late/duplicate items, investigation and export.
- Exit: money movement, accounting, delivery and reconciliation evidence are distinct
  and runtime-proven.

### E8 — SDD and additional schemes

- Scope: none until a separate product/source/architecture decision.
- Candidates: SDD Core, SDD B2B, OCT Inst and VoP capability trains.
- Exit: independent use-case and source readiness, not automatic backlog expansion.

## 8. First-cohort selection

| Candidate | Value | Source readiness | Existing implementation | Risk | Size | Recommendation |
|---|---:|---:|---:|---:|---:|---|
| Signed pain.001 pilot | high | medium-high | high, incomplete profile/UI | medium | 6 records | **select** |
| Implemented-capability reconciliation only | medium | high | very high | low | 4–8 | E2 after pilot contract |
| File multi-instruction | high | low | low | high | large | defer E3 |
| SCT routing/STEP2 or STET | high | low/restricted | low | high | large | defer E4 |
| SCT Inst/TIPS/RT1 | high | medium public, restricted participant | partial primitives | very high | very large | defer E5 |

The signed pilot is the smallest slice that exposes source-to-runtime gaps while using
real implementation. A reconciliation-only cohort would reduce warnings but would not
prove that the new contract governs delivery.

## 9. E1 story contracts

All six records have:

```yaml
semantic_enforcement: ENFORCED
use_case_id: UC-SCT-002
business_process_id: BP-01
actor_or_external_system: authenticated payment submitter
actor_goal: submit one source-qualified signed SCT instruction and obtain durable evidence
main_flow: authenticate -> archive -> verify -> validate -> map -> persist -> expose outcome
source_classification: HUMAN_REVIEW_REQUIRED
source_tags: [EPC-SCT, ISO20022, PROJECT-ADR, OPEN-QUESTION]
source_evidence: [SE-E1-SCT-C2PSP-001, SE-E1-SCT-TVS-001, SE-E1-ADDRESS-001]
applicable_rules: [BR-SCT-003]
security_context: tenant claim, role, RLS, least privilege, redacted telemetry
quality_scenarios: [QS-SEC-01, QS-SEC-02, QS-REL-01]
current_readiness: HUMAN_REVIEW_REQUIRED
candidate_after_gates: READY
blocking_decisions: [channel, signature, TVS, limits, evidence-visibility]
human_review_state: NOT_REVIEWED
```

Specific `slice_id`, owner, realization and verify remain per record:

### `31.2` — signature profile and verdict evidence

- **Business outcome:** reject invalid/untrusted signed input before parsing while
  retaining provenance.
- **Source authority:** project security decision plus reviewed channel profile; EPC
  does not mandate Debina's Ed25519 envelope.
- **Scope:** algorithm allowlist, key/certificate lineage, validity/rotation and verdict.
- **Non-goals:** custom cryptography, rail egress signing, scheme-compliance claim.
- **Backend implementation:** reconcile existing `SignatureVerificationPort`.
- **Database implementation:** preserve append-only signature evidence and ownership.
- **Kafka/events:** none required for the verdict transaction.
- **Security/Keycloak:** submitter role and privileged key administration separated.
- **API/contracts:** versioned signature metadata contract; no private key input.
- **BFF:** pass reviewed metadata without interpreting signature semantics.
- **Frontend:** display verdict/evidence metadata only.
- **Observability:** reason code and correlation, never signature/raw XML/key material.
- **Tests:** crypto unit, key-state/database and ordering integration.
- **Runtime verify:** real signed/tampered/missing-signature composed cases.
- **Migration/compatibility:** current project Ed25519 profile is explicitly versioned.
- **Acceptance criteria:** invalid verdict cannot reach parser; every attempt is auditable.
- **Dependencies/Risks:** security/channel decision; certificate expiry and rotation.

### `19.2` — immutable archive-before-verify-before-parse

- **Business outcome:** preserve submitted evidence and prevent unsafe parsing.
- **Source authority:** project evidence/security policy.
- **Scope:** exact bytes, checksum, correlation/causation/idempotency and ordering.
- **Non-goals:** XSD/TVS or business validation.
- **Backend/Database:** reconcile existing pipeline and ingress-owned immutable archive.
- **Kafka/events:** no event before durable intake outcome.
- **Security/API/BFF/Frontend:** no raw payload leakage or UI ownership.
- **Observability:** stage timing and verdict, redacted.
- **Tests:** mutation-proven integration ordering; archive immutability/RLS database test.
- **Runtime verify:** rejected signature still has evidence and never invokes parser.
- **Migration/compatibility:** preserve existing identifiers.
- **Acceptance/Dependencies/Risks:** exact-byte proof; storage/retention legal review.

### `19.4` — source-qualified single pain.001 intake

- **Business outcome:** accept or deterministically reject one applicable SCT customer
  instruction.
- **Source authority:** EPC132-08 2025 v1.0, applicable TVS and ISO `.001.09`.
- **Scope:** namespace/profile detection, safe parse, XSD, TVS, business validation,
  one-transaction mapping and persistence.
- **Non-goals:** multi-instruction partial processing or CSM submission.
- **Backend:** streaming-versus-DOM decision, layered errors and reviewed limits.
- **Database:** owner-schema lineage, business uniqueness and idempotency constraints.
- **Kafka/events:** outbox only after committed accepted outcome.
- **Security/Keycloak:** tenant/role, XXE/entity/depth/text/size limits.
- **API/contracts:** REST command with deterministic accepted/rejected/idempotent result.
- **BFF/Frontend:** delegated to the proposed `EPIC-24/24.10`.
- **Observability:** validation class/reason/count, never XML or party/account data.
- **Tests:** XSD/TVS fixtures, mapping/limits, DB/RLS and composed intake.
- **Runtime verify:** accepted, corrupt XML, wrong namespace, invalid profile, duplicate.
- **Migration/compatibility:** existing accepted records remain readable.
- **Acceptance/Dependencies/Risks:** no “ISO latest” claim; TVS license and channel gate.

### `26.3` — field lineage and canonical mapping

- **Business outcome:** trace source message/instruction/end-to-end/transaction IDs
  without collapsing their meanings.
- **Source authority:** EPC/ISO field paths plus project ownership.
- **Scope:** field-level mapping template, normalization, nullability and raw lineage.
- **Non-goals:** an aggregate per XML noun or direct element-to-column mirroring.
- **Backend/Database:** ISO writer owns lineage; payment owner consumes only ports.
- **Kafka/events:** identifier-bearing contracts are versioned and minimised.
- **Security/API/BFF/Frontend:** role-appropriate, masked operational read.
- **Observability:** hashed/carefully classified identifiers where needed.
- **Tests:** mapping unit, constraint/RLS/database and one integration proof.
- **Runtime verify:** displayed IDs reconcile to archived message evidence.
- **Migration/compatibility:** review existing rows before tightening constraints.
- **Acceptance/Dependencies/Risks:** `DATABASE_MAPPING_REVIEW`; duplicate semantics.

### `26.4` — source-owned operational lineage read

- **Business outcome:** authorized operator can inspect lineage without direct table
  access.
- **Source authority:** ADR-N17 and module ownership.
- **Scope:** query-only GraphQL contract, source-owned fetcher, BFF allowlist and UI panel.
- **Non-goals:** GraphQL mutation or GraphQL-owned payment model.
- **Backend/Database/Kafka:** no new writer; existing read contract only.
- **Security/Keycloak:** roles, tenant/RLS and field visibility.
- **API/BFF/Frontend:** generated/drift-checked query and fixed destination.
- **Observability:** query name/timing/error, no sensitive variable logging.
- **Tests:** schema/allowlist contract and one composed read smoke.
- **Runtime verify:** tenant-authorized detail resolves; cross-tenant and mutation fail.
- **Migration/compatibility:** reconcile stale story prose.
- **Acceptance/Dependencies/Risks:** no direct cross-schema repository; access review.

### Proposed `EPIC-24/24.10` — signed upload actor journey

- **Business outcome:** authenticated user submits one signed XML and reaches its
  validation/payment evidence.
- **Source authority:** admitted E1 channel/project contract.
- **Scope:** one BFF command route, accessible upload/progress/outcome and detail link.
- **Non-goals:** broad Files workspace, drag-drop batch product, browser matrix.
- **Backend/Database/Kafka:** reuse E1 command; no UI-owned persistence/event.
- **Security/Keycloak:** session, role, CSRF/origin, content type/byte limit, no raw logs.
- **API/contracts:** REST owns command; GraphQL remains query-only.
- **BFF:** streaming forwarder with deterministic error translation.
- **Frontend:** loading/error/invalid-signature/validation/duplicate/accepted states.
- **Observability:** request correlation and redacted outcome.
- **Tests:** BFF contract/component tests and exactly one Playwright happy-path smoke.
- **Runtime verify:** Dagger login → signed upload → result/detail; optional approval only
  if policy applies to the fixture.
- **Migration/compatibility:** no change to existing list/detail URLs.
- **Acceptance/Dependencies/Risks:** product/security/accessibility copy and exposure review.

## 10. Architecture realization

```yaml
- use_case: UC-SCT-002
  slice: UCS-SCT-002-A
  primary_actor: authenticated payment submitter
  goal: durably submit and validate one signed SCT instruction
  source_classification: VERIFY_PER_USE
  source_tags: [EPC-SCT, ISO20022, PROJECT-ADR, OPEN-QUESTION]
  module_owners: [ingress, signature, ISO-adapter, payment-lifecycle]
  command_port: signed pain.001 intake REST command
  query_port: none in A
  database_schema: [ingress, signature, iso, payment]
  events: payment accepted outbox only after commit
  kafka_topics: existing payment lifecycle topic; no new topic in planning
  outbox: yes
  inbox: no for REST intake
  rest: POST /api/v1/iso/pain001
  grpc: none
  graphql: none for commands
  bff: proposed EPIC-24/24.10 fixed backend command route
  frontend_workspace: Payments
  keycloak_roles: reviewed submitter role; key administration separate
  rls: tenant-scoped owner policies on mutable/lineage records
  observability: trace/correlation/stage/reason metrics with strict redaction
  quality_scenarios: [QS-SEC-01, QS-SEC-02, QS-REL-02, QS-TRC-01]
  executable_verify: focused tests plus Dagger E1 signed-upload acceptance
  architecture_outcome: CURRENT_ARCHITECTURE_SUFFICIENT
- use_case: UC-SCT-002
  slice: UCS-SCT-002-B
  primary_actor: authenticated payment submitter
  goal: receive a deterministic rejection when signature verification fails
  source_classification: DECISION_BLOCKED
  source_tags: [PROJECT-ADR, OPEN-QUESTION]
  module_owners: [ingress, signature, evidence-audit, nextjs-bff]
  command_port: signed pain.001 intake REST command
  query_port: safe provenance result only; no payment-detail query because no payment is mapped
  database_schema: [ingress, signature, audit]
  events: none
  kafka_topics: none
  outbox: no
  inbox: no
  rest: deterministic safe signature-rejection response
  grpc: none
  graphql: no raw evidence and no command
  bff: fixed destination, session-aware error translation
  frontend_workspace: Payments
  keycloak_roles: submitter; evidence access remains separately restricted
  rls: tenant isolation on raw and verdict evidence
  observability: safe verdict/reason/correlation only; no XML or signature bytes
  quality_scenarios: [QS-SEC-02, QS-REL-02, QS-TRC-01]
  executable_verify: archive-before-verify ordering plus rejected-upload component proof
  architecture_outcome: CURRENT_ARCHITECTURE_SUFFICIENT
- use_case: UC-SCT-003
  slice: UCS-SCT-003-A
  primary_actor: file-channel submitter
  goal: submit a multi-instruction file with deterministic file and item outcomes
  source_classification: SOURCE_BLOCKED
  source_tags: [EPC-SCT, ISO20022, OPEN-QUESTION]
  module_owners: [ingress, ISO-adapter, payment-lifecycle]
  command_port: not admitted
  query_port: not admitted
  database_schema: not admitted
  events: not admitted
  kafka_topics: not admitted
  outbox: required if admitted
  inbox: channel-dependent
  rest: undecided
  grpc: none planned
  graphql: future source-owned reads only
  bff: future Files journey
  frontend_workspace: Files
  keycloak_roles: review required
  rls: mandatory for tenant data
  observability: file/group/item counts without sensitive data
  quality_scenarios: [QS-INT-01, QS-REL-02]
  executable_verify: unavailable until decisions resolve
  architecture_outcome: AGGREGATE_REVIEW_REQUIRED
- use_case: UC-CLEARING-001
  slice: UCS-CLEARING-001-A
  primary_actor: clearing operations system
  goal: submit a rail-qualified SCT item and retain receipt evidence
  source_classification: PARTICIPANT_DOCUMENTATION_REQUIRED
  source_tags: [EPC-SCT, STEP2, STET]
  module_owners: [ISO-adapter, egress]
  command_port: NEW_PUBLIC_PORT
  query_port: NEW_READ_MODEL
  database_schema: owner review required
  events: versioned submission/receipt events
  kafka_topics: topic contract review required
  outbox: yes
  inbox: yes
  rest: operator commands only if admitted
  grpc: participant profile dependent
  graphql: operational reads only
  bff: no rail semantics
  frontend_workspace: Route and delivery operations
  keycloak_roles: operations roles review
  rls: mandatory
  observability: delivery/receipt/latency/retry/DLQ
  quality_scenarios: [QS-DEP-01, QS-REL-02]
  executable_verify: synthetic adapter contract or lawful participant sandbox
  architecture_outcome: NEW_INTEGRATION_CONTRACT
- use_case: UC-SCTINST-001
  slice: UCS-SCTINST-001-A
  primary_actor: instant-payment submitter
  goal: submit an eligible instant instruction and obtain explicit finality evidence
  source_classification: PARTICIPANT_DOCUMENTATION_REQUIRED
  source_tags: [EU-LAW, EPC-SCT-INST, TIPS, RT1, STET]
  module_owners: [payment-lifecycle, routing, settlement, ledger]
  command_port: reviewed instant submission port
  query_port: separate eligibility/finality reads
  database_schema: existing owners unless admission proves otherwise
  events: separate acceptance, settlement, finality and availability evidence
  kafka_topics: versioned per accepted contract
  outbox: yes
  inbox: yes
  rest: command owner only
  grpc: rail dependent
  graphql: query-only
  bff: session adapter only
  frontend_workspace: Payments and instant operations
  keycloak_roles: reviewed
  rls: mandatory
  observability: latency, timeout, liquidity and finality evidence
  quality_scenarios: [QS-INT-02, QS-REL-01, QS-DEP-01]
  executable_verify: concurrency/failure composed proof
  architecture_outcome: BOUNDARY_REVIEW_REQUIRED
- use_case: UC-STATUS-001
  slice: UCS-STATUS-001-A
  primary_actor: external status source
  goal: correlate a status without inventing lifecycle or finality
  source_classification: VERIFY_PER_USE
  source_tags: [EPC-SCT, ISO20022]
  module_owners: [ISO-adapter, payment-lifecycle, settlement]
  command_port: inbound status correlation
  query_port: source-owned status/evidence read
  database_schema: separate source owners
  events: correlated/unmatched/duplicate status facts
  kafka_topics: reviewed inbox contract
  outbox: qualified transitions only
  inbox: yes
  rest: operator investigation commands only
  grpc: none by default
  graphql: operational reads only
  bff: no correlation semantics
  frontend_workspace: Payments and Cases
  keycloak_roles: operator/investigator
  rls: mandatory
  observability: unmatched/late/out-of-order/retry metrics
  quality_scenarios: [QS-REL-01, QS-TRC-01]
  executable_verify: status ordering/correlation and finality-negative proofs
  architecture_outcome: CURRENT_ARCHITECTURE_SUFFICIENT
- use_case: UC-SETTLEMENT-001
  slice: UCS-SETTLEMENT-001-A
  primary_actor: settlement adapter
  goal: record authoritative settlement/finality and reconcile money movement
  source_classification: PARTICIPANT_DOCUMENTATION_REQUIRED
  source_tags: [PROJECT-ADR, TIPS, STEP2, RT1, STET]
  module_owners: [settlement, ledger]
  command_port: rail-qualified settlement evidence
  query_port: settlement/reconciliation operational read
  database_schema: settlement, ledger and evidence owners
  events: settlement and finality facts remain separate
  kafka_topics: reviewed
  outbox: yes
  inbox: yes
  rest: no external finality fabrication
  grpc: rail dependent
  graphql: query-only
  bff: adapter only
  frontend_workspace: Settlement and Reconciliation
  keycloak_roles: settlement/reconciliation operator
  rls: mandatory
  observability: settlement latency, mismatch, retry and audit
  quality_scenarios: [QS-INT-02, QS-TRC-01]
  executable_verify: one-transaction and failure-injection proof
  architecture_outcome: BOUNDARY_REVIEW_REQUIRED
```

SDD/OCT Inst/VoP slices are not admitted in this plan; E8 must run source discovery,
Use-Case 2.0 and architecture admission before creating an architecture realization.

## 11. XML → domain → database mapping

The canonical method is
`docs/data/ISO-XML-DOMAIN-DATABASE-MAPPING-METHOD.md` with template
`docs/data/ISO-XML-DOMAIN-DATABASE-MAPPING-TEMPLATE.yaml`.

The design separates:

```text
external XML structure
→ canonical payment concept
→ aggregate/entity/value object
→ technical lineage/evidence
→ owner schema and command port
→ purpose-built read model
```

It rejects one-column-per-element mapping and noun-driven aggregate invention.
Original bytes are retained as immutable evidence with checksum and controlled access;
parsed/normalized data is separately owned. Signature/certificate lineage is immutable
metadata, not private-key storage. Parser strategy, payload/count/depth/text limits and
retention are review decisions. Message, group, instruction, end-to-end, transaction and
UETR identifiers retain distinct semantics. Parties, agents, IBAN/BIC, amounts, dates,
remittance, purpose, charges, addresses, reasons and supplementary data are mapped only
when an admitted slice needs them.

Every candidate aggregate passes `architecture-evolution-review`; unresolved candidates
are `AGGREGATE_REVIEW_REQUIRED`. Every database mapping passes ownership, Flyway, RLS,
data-integrity, testing and independent database review skills.

## 12. Full-stack implementation backlog

| Area | E1 | Later cohorts | Gate |
|---|---|---|---|
| Backend | layered signed intake and deterministic errors | file, routing, CSM, status, settlement, reconciliation | source/use-case contract |
| Database | evidence/lineage constraints, RLS, idempotency review | admitted owner schemas/read models | one writer, Flyway, DB review |
| Kafka | retain accepted outbox semantics | versioned per-use-case contracts, inbox/retry/DLQ | no topic without contract |
| Keycloak | reviewed submitter/operator/key-admin separation | rail/reconciliation/investigation roles | project policy + RLS proof |
| REST | command ownership | later commands by owner | no GraphQL mutations |
| GraphQL | existing lineage reads only | source-owned operational reads | ADR-N17 drift/allowlist |
| BFF | one signed-upload command adapter | journey adapters | session/CSRF/redaction |
| Frontend | upload outcome → existing detail | Files, route, settlement, cases after backend | capability-first |
| Dagger | one composed E1 acceptance | cohort-specific runtime proof | local authoritative platform |
| Observability | stage/reason/correlation/redaction | route/CSM/lag/DLQ/finality/mismatch | no sensitive XML/data |
| Operations | E1 runbook, replay restrictions, certificate alert | CSM/reconciliation/incident runbooks | human control and audit |

Required operational telemetry includes trace/correlation IDs; message/file/group/item
counts; validation reasons; route/CSM/settlement latency; Kafka lag/retries/DLQ;
reconciliation mismatch; certificate expiry; source freshness; replay and audit access.
Logs must not contain full XML, accounts, party names, addresses, secrets, raw signatures
or private certificate material.

## 13. Test allocation

`docs/testing/SEPA-PAYMENT-TEST-ALLOCATION-MATRIX.md` assigns one primary layer per
behavior and permits a secondary layer only for a different risk.

- Static/source/schema: source freshness, XSD/TVS, mapping and architecture drift.
- Unit: values, normalization, rules, transitions and mappings.
- Property-based: only identifiers, amount/currency, IBAN/BIC, text boundaries,
  normalization and control sums.
- Contract: OpenAPI, GraphQL, Kafka, XML profile, BFF and generated clients.
- Database: Flyway, constraints, RLS/grants, ownership, idempotency and retention.
- Integration: Spring/PostgreSQL/Kafka/Keycloak/outbox/inbox/XML/correlation.
- Component/journey: accepted/rejected/duplicate/approval/status/reconciliation risks.
- Dagger: composed runtime proof with ephemeral services and drift checks.
- Playwright: exactly one E1 login/upload/result-or-detail smoke; approval is included
  only if the selected fixture requires it.

No browser matrix, visual regression suite or UI duplication of backend validation cases
is authorized.

## 14. Quality scenarios

| Risk | Allocation |
|---|---|
| large file / many transactions | E3 `QUALITY_EXPERIMENT_REQUIRED`; measured limits |
| instant latency | E5 source threshold plus experiment; no arbitrary TPS |
| duplicate/replay | E1 idempotency, later inbox/outbox |
| out-of-order Kafka | E6 correlation/inbox |
| unavailable CSM / retry / DLQ | E4/E5 adapter experiment |
| database failure / recovery | owner transaction and composed failure injection |
| partial failure | E3 decision then test |
| Keycloak outage | BFF/API degraded-path proof |
| certificate expiry / invalid signature | E1 security integration |
| corrupted XML / XXE | E1 parser and endpoint integration |
| tenant isolation / restricted evidence | database plus API negative proof |
| log redaction | static/component telemetry assertion |
| reconciliation mismatch | E7 component and composed proof |

## 15. Security, legal and operational boundaries

Phase E plans Keycloak realm/client/roles, maker/checker segregation, tenant claims,
RLS, least privilege, provenance, certificate trust/rotation, channel mTLS where
required, encryption, audit/non-repudiation, secrets, redaction, minimisation,
retention, malware scanning, XXE protection, rate limiting and privileged evidence
export. It does not design custom cryptography.

Fraud, VoP, sanctions and AML are separate capability boundaries. Maker-checker is
project policy. Applicability, retention periods, incident reporting and regulatory
reporting require legal review; the plan does not market their presence as compliance.

## 16. Source gaps and decisions

Blocking or gating items are in `SOURCE-GAP-REGISTER.yaml`. The most important are:

- `DECISION_BLOCKED`: E1 channel, detached signature representation, trust profile,
  payload/transaction limits and error disclosure;
- `SOURCE_BLOCKED`: lawful applicable EPC TVS artifacts and complete rail profiles;
- `PARTICIPANT_DOCUMENTATION_REQUIRED`: STEP2, RT1, TIPS/MyStandards and STET
  participant details;
- `LEGAL_REVIEW_REQUIRED`: IPR/PSD2/PSD3-PSR status, reporting, sanctions/VoP,
  FTR/AML, DORA/NIS2, GDPR retention and evidence access;
- `QUALITY_EXPERIMENT_REQUIRED`: parser/file/instant/CSM thresholds;
- `SKILL_GAP`: no dedicated active source-evidence skill; existing source policy and
  validators are sufficient for E0/E1 planning, so this is non-blocking;
- `SKILL_GAP`: BFF skill contains an npm-shaped example while repository instructions
  require pnpm; repository authority resolves it, so no new skill is created;
- Dagger Engine socket unavailable in this session; Phase D remains closed because this
  is an environment diagnostic, not a discovered platform defect.

## 17. Skills execution matrix

| Obszar | Skill | Dlaczego uruchomiony | Oczekiwany output |
|---|---|---|---|
| Use cases | `enterprise-use-case-engineering` | source gate and actor-goal/slice discipline | admitted E1 flow and blocked later slices |
| Payment semantics | `source-backed-payments-modeling` | prevent ISO/scheme/rail conflation | qualified vocabulary and evidence gaps |
| Architecture | `architecture-evolution-review` | assess ports, modules, aggregates and reads | sufficiency/admission outcomes |
| Planning | `planning-semantic-integrity` | reconcile warnings and claims | cohort warning contract |
| Planning artifacts | `artifact-derived-planning`, `epic-story-task-catalog` | proposal structure and vertical stories | non-canonical change proposals |
| ISO lineage | `debina-iso20022-validation-lineage` | messages, IDs, XSD/TVS and raw evidence | ISO coverage and mapping method |
| Data integrity | `sepa-nexus-payments-data-integrity` | IDs, money, evidence and correlation | field mapping constraints |
| PostgreSQL/Flyway/RLS | `postgres-rls-migration`, `sepa-nexus-flyway-safe-change`, `sepa-nexus-database-testing`, `sepa-nexus-database-review` | ownership, grants, RLS and proof gates | implementation task/test/review gates |
| Transactions | `debina-postgres-transaction-coordination` | cross-schema command planning | explicit coordinator gate |
| Modulith | `spring-modulith-module` | prevent unjustified modules | admission requirements |
| Kafka | `debina-kafka-payment-contract` | topic/event/outbox/inbox discipline | versioned future contracts |
| Keycloak | `keycloak-realm-config` | role/client planning | least-privilege task gates |
| BFF | `nextjs-bff-route` | session-safe command adapter | E1 route contract |
| Frontend | `impeccable`, `shadcn-component-scaffold` | accessibility and current UI language | scoped journey, no screen implementation |
| Money movement | `debina-money-movement-invariants` | LedgerPort boundary | E5/E7 invariant gates |
| Finality | `debina-payment-state-finality` | five axes and evidence | no delivery-derived finality |
| Runtime proof | `debina-runtime-proof-testing` | claim-to-runtime discipline | cohort executable verifies |
| Dagger | `dagger-go-pipeline` | authoritative composed checks | E1 acceptance plan; no Phase D reopen |
| Handoff | `session-handoff` | exact continuation state | final `HANDOFF.md` |
| Source evidence | `SKILL_GAP` | no dedicated active skill in registry | current policy/validators sufficient; reassess only after repeated defect |

`impeccable` found no product context file (`NO_PRODUCT_MD`); existing Payments
workspace patterns are the E1 design baseline. This is non-blocking.

## 18. Warning burn-down

```yaml
- cohort: E0
  stories: 304 inventoried; 0 migrated
  use_case_warnings_before: 296
  use_case_warnings_resolved: 0
  use_case_warnings_introduced: 0
  use_case_warnings_after: 296
  planning_warnings_before: 69
  planning_warnings_resolved: 0
  planning_warnings_introduced: 0
  planning_warnings_after: 69
- cohort: E1
  stories: [EPIC-31/31.2, EPIC-19/19.2, EPIC-19/19.4, EPIC-26/26.3, EPIC-26/26.4, EPIC-24/24.10]
  use_case_warnings_before: 5 on existing selected records; EPIC-24/24.10 is proposal-only and not canonical
  use_case_warnings_resolved: target 5
  use_case_warnings_introduced: target 0
  use_case_warnings_after: target 0 on the six cohort records
  planning_warnings_before: 2 on selected existing records
  planning_warnings_resolved: target 2
  planning_warnings_introduced: target 0
  planning_warnings_after: target 0 on the six cohort records
- cohort: E2
  stories: select only after E1 exit
  use_case_warnings_before: MEASURE_AT_ENTRY
  use_case_warnings_resolved: MEASURE_AT_EXIT
  use_case_warnings_introduced: 0
  use_case_warnings_after: MEASURE_AT_EXIT
  planning_warnings_before: MEASURE_AT_ENTRY
  planning_warnings_resolved: MEASURE_AT_EXIT
  planning_warnings_introduced: 0
  planning_warnings_after: MEASURE_AT_EXIT
- cohort: E3-E8
  stories: not selected
  use_case_warnings_before: MEASURE_AT_COHORT_ENTRY
  use_case_warnings_resolved: MEASURE_AT_COHORT_EXIT
  use_case_warnings_introduced: 0
  use_case_warnings_after: MEASURE_AT_COHORT_EXIT
  planning_warnings_before: MEASURE_AT_COHORT_ENTRY
  planning_warnings_resolved: MEASURE_AT_COHORT_EXIT
  planning_warnings_introduced: 0
  planning_warnings_after: MEASURE_AT_COHORT_EXIT
```

No story may exit as `ENFORCED` with its own legacy warning. Global legacy warnings are
not an E1 exit criterion.

## 19. Human review queues

| Queue | Reviewer role | Artifacts | Blocking questions | Approval evidence |
|---|---|---|---|---|
| `PAYMENTS_DOMAIN_REVIEW` | SEPA product/domain specialist | scheme/ISO matrices, E1 rules/use case | correct scheme/profile/actor semantics? | dated signed review record |
| `LEGAL_REGULATORY_REVIEW` | qualified legal/regulatory reviewer | regulation matrix, retention/reporting gaps | applicability and legal basis? | reviewed issue log; no AI compliance claim |
| `ISO_MESSAGE_REVIEW` | ISO 20022/EPC specialist | message matrix, mapping template, TVS | exact version/path/cardinality/profile? | field-level approval |
| `DATABASE_MAPPING_REVIEW` | data/security owners | mapping method and E1 field map | owner, type, constraint, RLS, retention? | database review report |
| `SECURITY_REVIEW` | Keycloak/crypto/application security | channel/signature/BFF/evidence | trust, rotation, CSRF, redaction and access? | threat-model decision |
| `ARCHITECTURE_REVIEW` | Modulith/domain architects | realization records/ports | current boundary sufficient? | lifecycle/admission record or no-change approval |
| `PRODUCT_REVIEW` | Product Manager/Owner | actor journey and cohort scope | E1 outcome/non-goals/copy acceptable? | cohort acceptance |
| `QA_REVIEW` | QA/SDET architect | allocation matrix and verifies | primary layer and non-duplicate secondary proof? | approved test charter |

## 20. Milestones

1. `E0 baseline accepted` — source and repository inventory reviewed.
2. `E1 source review accepted` — TVS/channel/signature/limit decisions recorded.
3. `E1 first cohort ENFORCED` — six records canonical with zero local warnings.
4. `E1 first cohort runtime-proven` — allocated checks and Dagger acceptance pass.
5. `E2 implemented-capability reconciliation` — no rebuild, stale planning corrected.
6. `E3 next feature-train decision` — file cohort admitted or remains blocked.

## 21. Definition of Ready

A cohort is READY only when:

- official/current and relevant historical sources have versions, dates, sections,
  addenda/clarification checks and lawful access state;
- each atomic claim maps to a rule, process, use case, flow and behavioral slice;
- actor, goal, scope, non-goals and source/project distinction are explicit;
- architecture sufficiency/admission, data owner, transaction, RLS and event contracts
  are decided;
- story contract is `ENFORCED` and human review state is honest;
- each behavior has one primary test layer and an executable runtime verify;
- blocked legal/source/participant/quality decisions are not hidden.

## 22. Definition of Done

A cohort is DONE only when:

- its full-stack outcome is implemented without violating ADR-N1…N17;
- XSD/TVS/profile and mapping evidence is version-pinned where applicable;
- migrations, grants, RLS, ownership, idempotency and retention semantics are proven;
- outbox/inbox and Kafka contracts are proven if used;
- authentication/authorization/tenant boundaries and log redaction pass;
- REST commands and query-only GraphQL ownership remain separated;
- Dagger composed runtime proof passes and Playwright stays minimal;
- source, architecture, planning, capability and warning records match runtime truth;
- selected records have zero own legacy warnings;
- protected generated javadoc remains unchanged unless separately authorized.

## 23. Risks and controls

| Risk | Control |
|---|---|
| source/version drift | freshness dates, change register and cohort source gate |
| false EPC/ISO/CSM claim | atomic evidence and explicit project/participant qualification |
| rail leakage into core | separate adapter profiles and context-map review |
| XML-noun aggregate invention | mapping method plus aggregate admission |
| status/finality collapse | five-axis model and explicit evidence tests |
| money mutation outside LedgerPort | invariant and transaction runtime proof |
| planning drift | proposal-first migration and semantic validator |
| test duplication | primary-layer allocation and named secondary risk |
| frontend-first domain design | backend capability/source gate before UI |
| sensitive evidence leakage | RLS, role reads, redaction and negative tests |
| restricted-document misuse | metadata only and participant-documentation gate |

## 24. Commit plan

Recommended local commits after validators:

1. `docs(standards): inventory phase e payment sources`
2. `docs(data): define iso xml domain database mapping`
3. `docs(planning): reconcile phase e backlog candidates`
4. `docs(program): add controlled backlog migration plan`
5. `docs(program): record phase e planning handoff`

Files are staged explicitly. No push, rebase, broad add or destructive Git operation is
authorized.

## 25. Deferred work

- Production backend/frontend/database/Kafka/Keycloak/Dagger changes.
- Canonical edits to the six E1 stories before human review.
- Complete field mapping instances for E1 before TVS/profile acceptance.
- Participant-only STEP2/RT1/TIPS/STET contracts.
- E3 File/Group aggregate and partial-processing decision.
- Full SCT/SCT Inst clearing, settlement, R-transactions and reconciliation.
- SDD, OCT Inst and VoP implementation trains.
- Broad reporting, browser coverage, visual testing and remote CI.
- A new source-evidence skill unless repeated work proves a real reusable gap.

## 26. Recommended next cohort

Start E1 only after the channel/signature contract, applicable TVS material, exact
resource limits and review ownership are accepted. Then migrate the six selected
records as one controlled vertical slice. Do not begin with E3 files or a CSM adapter:
they multiply unresolved aggregate, participant-profile and finality risks.

The immediate next action is a human `PAYMENTS_DOMAIN_REVIEW` +
`ISO_MESSAGE_REVIEW` + `SECURITY_REVIEW` of the E1 source and channel decision pack,
followed by a field-level `DATABASE_MAPPING_REVIEW`.
