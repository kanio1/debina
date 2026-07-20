# Aneks C — pełny gap register, ryzyka i backlog uzupełniający

**Stan:** 2026-07-17. Ten aneks jest częścią [raportu głównego](../DEBINA-COMPREHENSIVE-PAYMENTS-ASSESSMENT.md). Rejestr ma **45 luk: 6 BLOCKER, 13 CRITICAL, 16 HIGH, 8 MEDIUM, 2 LOW**. `BUS-GAP-001` i `SCHEME-GAP-001` są blockerami warunkowymi: wobec celu pełnego huba z briefu, nie wobec obecnego, jawnie węższego CT learning lab.

## C.1. Pełna lista luk

### Tabela E — Gap register

| Gap ID | Severity | Obszar / problem | Dowód | Wpływ | Rekomendacja / właściciel / proponowany backlog |
|---|---|---|---|---|---|
| BUS-GAP-001 | BLOCKER* | Nierozstrzygnięty produkt: CT lab vs real multi-scheme hub | `AGENTS.md` mówi synthetic QA lab; brief wymaga pełnego huba | Bez scope gate backlog i compliance mają fałszywy cel | Product+architecture: zatwierdzony scope artifact/ADR; AC: products, roles, delegated systems, CSM/certification target jawne |
| SCHEME-GAP-001 | BLOCKER* | SDD Core/B2B i mandates nie istnieją | full-blueprint jawnie je deferuje; brak epic/schema/code | Nie można deklarować full SEPA | Product/SEPA: osobny discovery→ADR→epics po zmianie scope; nie dopisywać architektury bez źródła |
| SCHEME-GAP-002 | BLOCKER | Brak wersjonowanego standards→story traceability i certification profile | 0/76 external URL w `source:` | Brak dowodu EPC/CSM conformance | BA/SEPA: source registry + requirement IDs + effective dates + participant specs |
| FLOW-GAP-001 | BLOCKER | Brak outgoing CT E2E route→settle→ledger→render→send→receipt/finality | EPIC-29–64 głównie not-started | Payment przyjęty bez bezpiecznego wykonania | Payment team: pierwszy vertical slice na frozen ports, z crash/timeout/recon evidence |
| FLOW-GAP-002 | BLOCKER | Brak incoming SCT i SCT Inst pacs.008 | repo-wide absence | Hub nie jest dwukierunkowy | Product/ISO: source-backed inbound flow + ownership + tests; po scope approval |
| DATA-GAP-001 | BLOCKER | Ledger bilansuje `sum(amount_minor)` między różnymi walutami i nie ma FK account | V26 lines 60–93 | Fałszywie zbilansowany zapis / nieistniejące konto | Data/ledger: balance per currency albo single-currency invariant, FK/account ownership; Testcontainers negative tests |
| FLOW-GAP-003 | CRITICAL | Instant 10-second timeout/restore nie jest wykonawczy | generic EPIC-33/36 only; EU 2024/886 | Zablokowane środki i naruszenie IPR | Settlement/product: timer, authoritative clock, restore/idempotency, late result policy, audit |
| FLOW-GAP-004 | PARTIALLY REMEDIATED | Wcześniej `DISPATCHED` jako terminalny był zapisywany `is_final=true`; safety coupling usunięto | `PaymentTransitionTable` topology-only, `PaymentHistoryRecorder`, V30, finality mutation tests | Safety defect usunięty; brak nadal settlement-owned, profile-configured authority i trwałego pięcioosiowego modelu | Payment/settlement: odblokować EPIC-39/47 decyzją o `FinalityPolicy`/rekordzie/snapshot; zachować mutation tests |
| FLOW-GAP-005 | CRITICAL | Brak źródła explicit finality w runtime | settlement/finality epics not-started | Nie wiadomo, kiedy payment jest nieodwołalny | Settlement: profile-configured finality evidence; egress nie może ustawiać finality |
| MSG-GAP-001 | CRITICAL | Brak pacs.008 render/parse | EPC IG wymaga `.001.08`; code absent | Brak Inter-PSP SCT/SCT Inst | ISO/egress/ingress: approved library/profile, fixtures, lineage, CSM adapter |
| MSG-GAP-002 | CRITICAL | Brak wielowarstwowej ISO/EPC/CSM walidacji | brak XSD/TVS/SchemaFactory; only secure DOM | Schematycznie poprawne ≠ scheme-valid | ISO: version/profile registry, EPC TVS, business/reason/calendar matrices, CSM overlays |
| DATA-GAP-002 | CRITICAL | Reversal constraints incomplete | V26 allows missing/invalid/multiple reversal links | Podwójna/niepowiązana kompensacja | Ledger: CHECK/unique/conditional FK semantics + tests |
| DATA-GAP-003 | CRITICAL | Outbox/ISO/egress dispatcher roles istnieją, Spring używa `sepa_app` | V28, application.yml, runtime `permission denied` | Events/artifacts nie są wysyłane | Platform/data: dedicated datasource/transaction/GUC; startup and wiring integration tests |
| DATA-GAP-004 | CRITICAL | Brak triple-enforced `LedgerPort`, RESERVE/RELEASE/posting | schema-only; Story 32.2 open | Brak bezpiecznego money movement | Ledger/settlement: zamknąć decision packet, port + grants + architecture + DB tests |
| KAFKA-GAP-001 | CRITICAL | `payment.submitted.v1` na `payment.validated` i self-consumption | OutboxEvent/Dispatcher/InboxConsumer vs AsyncAPI | Fałszywe przejście bez walidacji, semantyczny loop | Integration/payment: event taxonomy/producer-consumer ownership + contract/mutation test |
| SEC-GAP-001 | CRITICAL | Target 12 ról vs realm 4; brak maker-checker i command audit | blueprint/EPIC-74 vs realm export | Nadmiarowe/brakujące uprawnienia operatorów | Security/product: role-action matrix, composites/service accounts, four-eyes workflow |
| INTEG-GAP-001 | CRITICAL | Brak realnych adapterów/certyfikacyjnych profili TIPS/RT1/STEP2/STET | tylko synthetic rows; participant docs unavailable | Brak integracyjnego pilota/certyfikacji | Integration owner: wybrać CSM, pozyskać UDFS/service docs, build simulator+adapter+cert pack |
| RECON-GAP-001 | CRITICAL | Reconciliation całkowicie projektowa | EPIC-57–64 0/27 done | Brak wykrycia settlement/ledger/egress mismatch | Recon: read-only snapshot/result/mismatch/case + repeatability tests |
| OPS-GAP-001 | CRITICAL | Brak recovery dla stuck/DLQ/orphan/unknown response | no commands/UI/runbook | Awaria wymaga edycji danych lub pozostaje ukryta | Operations: detector, queue, retry/release/escalate with expected-version, SoD, audit |
| FLOW-GAP-006 | HIGH | Reject/return/recall nie są procesami wykonawczymi | EPIC-30/42/65–72 design only | Negatywne ścieżki nie kończą się biznesowo | Case/ISO: osobne state/timing/reason/ledger rules, no `FAILED` collapse |
| FLOW-GAP-007 | HIGH | SCT claims/inquiries missing | EPC camt.027/camt.087/pacs.028 vs coarse story | Brak obsługi non-receipt/value date/status | Case/product: first-class claim catalog + timers + operator view |
| MSG-GAP-003 | HIGH | Source `CreDtTm` zastąpiony czasem processingu | CanonicalPaymentCommand/Pain001LineageRecorder | Fałszywy lineage/SLA/recon | ISO: preserve source timestamp + received/parsed timestamps separately; regression test |
| MSG-GAP-004 | HIGH | Brak exactly-one original lineage; unordered first lookup | V21 + IsoIdentifierLookup | Status/return może trafić do złej payment | ISO/data: conditional unique invariant + deterministic query + ambiguity queue |
| MSG-GAP-005 | HIGH | pacs.002 correlation nie ma inbound channel ani FSM consumer | service tested only; 27.3 blocked | Odpowiedzi CSM nie wpływają na lifecycle | ISO/integration: raw archive→validate→correlate→case/FSM event E2E |
| DATA-GAP-005 | HIGH | History/events bez FK, `max(seq)+1` race | V19 + PaymentHistoryRecorder | Orphan/gap/concurrent transition failure | Payment/data: transaction/flush redesign, expected version/unique transition; concurrency tests |
| DATA-GAP-006 | HIGH | Brak amount>0, scale validation; currency default | V3/DTO | Zaokrąglenie lub błędna kwota/waluta | Payment/data: value object + CHECK precision/positive/ISO currency explicit |
| DATA-GAP-007 | HIGH | JSON conflict rollbackuje raw evidence; key bez operation scope; no concurrency proof | PaymentService vs Pain001IngestionService/V10 | Utrata dowodu i cross-endpoint collision | Ingress: archive-before-decision, scoped idempotency tuple, concurrent test |
| DATA-GAP-008 | HIGH | One-writer/RLS/append-only incomplete | ingress/iso share `sepa_app`; raw table UPDATE; only 2 FORCE RLS | Tenant/write boundary bypass | Data/security: per-schema roles/grants, RLS applicability matrix, revoke update, tests |
| SEC-GAP-002 | HIGH | Brak `aud=sepa-api` enforcement | realm mapper absent; BFF/backend issuer-only | Realm token dla innego clienta może wejść do API | Security: audience mapper + validator + negative token tests |
| SEC-GAP-003 | HIGH | In-memory BFF sessions, no refresh, GET logout no CSRF, password grant enabled | auth routes/realm | Multi-instance loss/session/security weakness | Frontend/security: durable encrypted store, refresh rotation, POST logout, disable direct grants |
| KAFKA-GAP-002 | HIGH | 28 skeletal topics, no schema/retry/DLQ/replay/key contract | AsyncAPI `{}`, one listener | Duplicate/order/failure uncontrolled | Integration: versioned payloads, ownership, aggregate key, retry taxonomy, replay runbook |
| OBS-GAP-001 | HIGH | No tracing/MDC/alerts/outbox age/stuck metrics; HTTP correlation lost | CorrelationIdFilter/outbox creation | Brak E2E diagnosis/SLA evidence | Platform: OpenTelemetry/MDC propagation, metrics+dashboards+alerts |
| TEST-GAP-001 | HIGH | 0 frontend tests; no Playwright CI | frontend source/workflow | UI/security/operator regressions invisible | QA/frontend: component+BFF security+Playwright role journeys |
| TEST-GAP-002 | HIGH | Full suite nonhermetic; scheduler errors logged without failing tests | Keycloak localhost dependency; runtime permission errors | Zielone testy mogą ukrywać niedziałający runtime | QA/platform: controlled services, scheduler failure probes, separate integration phase |
| INFRA-GAP-001 | HIGH | Kafka `latest`, PLAINTEXT/start-dev, no backup/PITR/DR/health stack | compose/config | Non-reproducibility and operational loss | Platform: pin versions, TLS/secrets, restore drills/RPO-RTO, health/monitoring profiles |
| PLN-GAP-001 | MEDIUM | EPIC-14/44/45 index/frontmatter drift | planning README vs files | Błędny wybór pracy | Planning: parity validator and fix current values |
| PLN-GAP-002 | MEDIUM | Capability graph incomplete/stale | 68 story nodes; 261 NOT_DEEP_DIVED | Ukryte zależności i fałszywa traceability | Planning: story-level deep dive and status parity validation |
| PLN-GAP-003 | MEDIUM | Business owner/description/AC sparse | 0 owners; 98 descriptions; 50 explicit AC | Techniczne taski bez pełnego behavior | Product/BA: structured actors/start/end/negative/timing/manual/standard fields |
| BUS-GAP-002 | MEDIUM | Delegated boundaries for VoP/SCA/AML/fraud/core posting unresolved | no integration owner/contracts | Scope creep albo compliance hole | Product/security: responsibility matrix; open questions, not invented modules |
| OPS-GAP-002 | MEDIUM | No camt.052/053/054 reporting, EOD/financial close | reporting/recon modules absent | Operacje nie zamykają dnia | Reporting/recon: source-backed reporting capability after first E2E |
| DATA-GAP-009 | MEDIUM | Brak retention/partition/archive/PITR/replication design | planning open question/infra absence | Wzrost tabel i brak recovery evidence | Data/platform: volume model, retention/legal boundary, backup/restore tests |
| ARCH-GAP-001 | MEDIUM | Several logical modules under one Modulith root package | `com.sepanexus.modules` package-info | Boundary tests weaker than intended | Architecture: enforce frozen module boundaries without adding new modules |
| TECH-GAP-001 | MEDIUM | Version governance inconsistent: Kafka latest, docs/target drift | pom/compose/blueprints | Nieodtwarzalny stack | Platform: version manifest/BOM/release-note gate; do not silently change frozen choices |
| UX-GAP-001 | LOW | Build fetches Google fonts; TanStack React Compiler warning | failed offline build; lint warning | Non-hermetic build/minor optimisation risk | Frontend: self-host fonts; document compiler exclusion/upgrade |
| DOC-GAP-001 | LOW | Brak jednolitego external source/message/profile registry | docs scattered | Audyt trzeba odtwarzać ręcznie | BA/docs: governed registry linked from planning index |

## C.2. Failure modes and risks

| Risk ID | Failure mode | Przyczyna / detekcja | Efekt | P×I | Kontrola wymagana |
|---|---|---|---|---|---|
| R-01 | CT accepted but never leaves Debina | dispatcher permission/topic mismatch; currently only logs | stuck payment, false customer status | H×C | health probe, outbox-age SLO, correct role wiring, ops queue |
| R-02 | cross-currency journal accepted | trigger sums minor units globally | financial inconsistency | M×C | per-currency/single-currency DB invariant |
| R-03 | dispatched treated as final | collapsed FSM | invalid return/recall/accounting decisions | H×C | five-axis state and source evidence |
| R-04 | wrong status attached | ambiguous/unordered lineage | wrong payment rejected/returned | M×C | unique identity/correlation + ambiguity case |
| R-05 | duplicate execution | crash/replay/concurrent idempotency untested | double money movement | M×C | business dedup + inbox + ledger uniqueness + concurrency tests |
| R-06 | instant timeout not restored | timer/late response absent | customer funds unavailable, IPR breach | H×C | 10-second saga/timer/late-result reconciliation |
| R-07 | tenant data exposure | incomplete RLS/audience/roles | confidentiality/SoD breach | M×C | end-to-end auth/RLS matrix and token tests |
| R-08 | certification false positive | public docs/XSD assumed sufficient | failed onboarding/certification | H×H | participant spec pack and formal traceability |
| R-09 | operator worsens state | no expected version/SoD/audit | race/double action | M×C | command workflow, maker-checker, immutable evidence |
| R-10 | recovery impossible | no PITR/DR/replay runbook | prolonged outage/data loss | M×C | RPO/RTO, restore drill, replay controls |

## C.3. Prioritised final backlog

The backlog below is supplementary and does not silently alter frozen architecture. Where source is missing it starts with a decision/discovery artifact, not implementation.

### Tabela I — Finalny backlog

| Priorytet | EPIC/story / capability | Uzasadnienie biznesowe | Ryzyko braku | Zależności | Rozmiar |
|---|---|---|---|---|---|
| A0 | Product & standards scope gate | Align lab vs real hub/products/CSM/compliance | entire backlog targets wrong product | user/team decision, ADR if architecture changes | S |
| A1 | Fix ledger currency/account/reversal invariants | Prevent invalid money evidence | financial corruption | EPIC-32 decision packet | M |
| A2 | Fix finality/five-axis implementation | Preserve frozen business truth | incorrect irreversible decisions | EPIC-39/47 | L |
| A3 | Fix outbox/ISO/egress DB role wiring and semantic Kafka contract | Make current spine operational | accepted but stuck/false validation | EPIC-15/18, dedicated datasource | M |
| A4 | Close LedgerPort RESERVE/POST/RELEASE model | Safe settlement side effects | double/unsupported money move | approved ledger model | L |
| A5 | First complete outgoing CT vertical slice | Prove business E2E | no usable payment product | A1–A4, routing/settlement/egress | XL |
| A6 | Runtime recovery/observability baseline | Detect and recover stuck flows | silent operational failure | A3/A5 | L |
| B1 | Versioned EPC/CSM source registry + validation profiles | Conformance evidence | failed partner onboarding | chosen product/CSM and participant docs | L |
| B2 | pacs.008 render/parse + pacs.002 real ingress | Inter-PSP exchange | no CSM communication | B1, ISO lineage decisions | XL |
| B3 | Security role/action/audience/SoD adoption | Safe partner/operator access | privilege/data breach | EPIC-74, RLS matrix | L |
| B4 | CSM simulator then one real adapter/cert pack | Integration readiness | lab-only forever | chosen CSM, restricted docs | XL |
| B5 | Incoming SCT/SCT Inst scope and flow | Bidirectional hub | incomplete product | A5/B1; explicit approval | XL |
| C1 | Kafka schemas, keys, retry/DLQ/replay | Safe asynchronous scale | order/duplicate failure | A3/A5 | L |
| C2 | Partition/retention/backup/PITR/DR drills | Volume and recoverability | outage/data growth | volume/RPO/RTO inputs | L |
| C3 | Performance/concurrency/failure-injection suite | Scale evidence | double/stuck under load | working E2E | L |
| D1 | SCT Inst 10-second/24×7/late-response/VoP boundary | IPR compliance | customer/regulatory harm | B2/B4 | XL |
| D2 | SCT return/reject/recall/RFRO | Scheme-complete negative flows | manual unsafe handling | A5/B1/B2 | XL |
| E1 | SDD discovery + frozen-scope change proposal | Decide if SDD belongs | accidental architecture invention | A0, EPC Core/B2B analysis | M |
| E2 | Mandate lifecycle + Core/B2B collection models | Full SDD | cannot debit compliantly | approved E1 | XL |
| E3 | SDD R/claims including non-XML channels | Complete exceptions | disputes/refunds unmanaged | E2, operational ownership | XL |
| F1 | SCT claims/inquiries (pacs.028/camt.027/camt.087) | Formal EPC processes | unresolved non-receipt/value date | D2, case/timers | L |
| F2 | Reconciliation snapshot/mismatch/case | Detect financial/transport divergence | undetected mismatch | A5, ledger/settlement/egress evidence | XL |
| F3 | Operator commands and maker-checker UI | Safe exception handling | data edits/operational dead-end | B3/F2 | XL |
| G1 | PostgreSQL 18 hardening | Use current stable capabilities safely | correctness/performance gaps | A1/A4, volume model | M |
| H1 | PostgreSQL 19 compatibility laboratory only | Learn without dependency | premature design lock | PG19 GA+patch maturity | S |
| I1 | Planning parity/full story deep-dive | Executable capability graph | hidden dependencies | no architecture change | M |
| I2 | Frontend/BFF/Playwright/security test layer | Verify role journeys and ops | UI/auth regressions | B3/F3 | L |

### Ordered waves

1. **Stop-the-line:** A0–A4. No money-bearing pilot before closure and regression evidence.
2. **First product truth:** A5–A6, one outgoing CT profile only.
3. **External integration:** B1–B4, then direction expansion B5.
4. **Instant and negative paths:** D1–D2, F1.
5. **Financial and operator closure:** F2–F3.
6. **Scale:** C1–C3 and G1.
7. **Product expansion:** E1–E3 only after explicit scope approval.
8. **Future research:** H1 after PostgreSQL 19 GA; no production dependency now.

## C.4. Acceptance criteria for the first five items

1. **Scope gate:** approved artifact states product/rail/direction/tenant/CSM/certification goals, delegated systems and non-goals; all new epics link to it.
2. **Ledger constraints:** DB rejects cross-currency entries, unknown accounts, malformed and duplicate reversals; tests run on PostgreSQL 18 and prove append-only grants.
3. **Finality:** business, ISO, finality, transport and receipt are stored/transitioned independently; DISPATCHED never implies final; return-after-finality creates a new related payment.
4. **Dispatcher/Kafka:** production Spring context uses intended roles/GUC; a Testcontainers run proves DB commit→outbox→correct topic/event→inbox exactly-once business effect under duplicate/crash; scheduler failure fails health/test.
5. **Outgoing CT slice:** pain.001/JSON intake completes route, settlement/ledger, pacs.008 render/sign/send, pacs.002/result, explicit finality, reconciliation and operator-visible evidence for happy, reject, timeout and duplicate scenarios.

## C.5. PostgreSQL 18/19 decisions

| Obszar | Stan PostgreSQL 18 | Ryzyko | Rekomendacja 18 | Możliwość PostgreSQL 19 | Decyzja |
|---|---|---|---|---|---|
| UUID | `gen_random_uuid()` | random index locality | evaluate `uuidv7()` for new technical IDs; never as idempotency | no dependency needed | WYKORZYSTAĆ 18 after compatibility test |
| Integrity | partial PK/unique/check/triggers | app-only invariants | add amount/currency/lineage/reversal/transition constraints | `FOR PORTION` may aid temporal ops | 18 now; 19 OBSERWOWAĆ |
| Async I/O | default container settings | assumed performance | benchmark AIO with realistic workload | monitoring evolves | 18 benchmark, not correctness dependency |
| Temporal | current history tables | race/incomplete FK | explicit immutable history + expected version; consider temporal constraints only where model fits | richer `FOR PORTION` | prepare experiments only |
| Upsert/idempotency | `ON CONFLICT` used | concurrency semantics incomplete | prove atomic outcomes and evidence | `ON CONFLICT DO SELECT RETURNING` may simplify | NIE UZALEŻNIAĆ until GA/maturity |
| Partition maintenance | no strategy | retention/locks | volume-driven PG18 partition/archival plan | concurrent REPACK/merge/split may help ops | OBSERWOWAĆ |
| Backup/replication | absent repo evidence | data loss | PITR/restore/logical replication drills now | sequence replication improvement | PG18 requirement; PG19 optional later |
| Observability | basic DB tests | invisible locks/vacuum | pg_stat/locks/autovacuum dashboards now | pg_stat_lock/recovery/autovacuum_scores | lab after GA |

PostgreSQL 19 Beta 2 (2026-07-16) is pre-release and unsupported for production. Categories: **WYKORZYSTAĆ W POSTGRESQL 18** — constraints, uuidv7 where justified, AIO benchmarking, RLS, logical replication/PITR; **PRZYGOTOWAĆ SIĘ** — portable tests and no reliance on internals; **OBSERWOWAĆ** — temporal DML, concurrent repack/partition operations and new stats; **NIE UZALEŻNIAĆ SYSTEMU** — every PG19 beta-only feature until GA plus patch maturity.
