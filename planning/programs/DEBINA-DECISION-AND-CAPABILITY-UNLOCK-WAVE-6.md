# Debina Decision and Capability Unlock Wave 6

## Baseline and historical reconciliation

- Baseline/local `HEAD`: `63fbf7fe07819c44a98743fd1b8f65ac2257cc8e` on `main`; the worktree was clean before this wave.
- Wave 5 is reconciled from its program record, `HANDOFF.md`, and commits `4c47158`, `435a382`, `63fbf7f`. Its deferred strategy, G6 FSM, netting, item finality, V51/V52, and cutoff read boundary remain historical evidence and are not replayed or counted.
- No production, migration, runtime configuration, or skill-discovery bridge changed. `.agents/skills -> ../.claude/skills` resolves to the canonical `.claude/skills` tree.

## Active skills and source authority

Applied: `artifact-derived-planning`, `epic-story-task-catalog`, `debina-runtime-proof-testing`, `spring-modulith-module`, `debina-money-movement-invariants`, `debina-payment-state-finality`, `debina-postgres-transaction-coordination`, `keycloak-realm-config`, and `official-docs-and-versioned-research`. Explicit requests prove loading, not implicit routing.

| Source | Blob | Relevant section |
|---|---|---|
| `sepa-nexus-message-flow-and-data-blueprint.md` | `f8667131109858da5ff7f3d3a92d74d31a1df900` | §§3.9--3.10, 4.6, 4.10, 4.11, §8 |
| `sepa-nexus-blueprint-ownership-integration.md` | `fc88d643a0e5c24e73f3f5cb32a2056ab646428b` | ownership/read model boundaries |
| `sepa-nexus-full-blueprint-review-and-task-plan.md` | `c715ec976a9fa6eb96acc20e5220a0ca35fe7570` | P1 deferrals and egress gaps |
| `sepa-nexus-decision-gate.md` | `e64de96408078ec0a4f3ef0d78480f1876cc8734` | no implicit fallback; queue/calendar scope |
| ADR-N10, ADR-N11, ADR-N12, ADR-N13 | accepted records | LedgerPort/finality, gross-only coordination, fallback and deferred-cycle boundaries |
| `sepa-nexus-keycloak-26-security-architecture-blueprint.md` | `4f7250d967d3bd2369ce70f5889df6965e78e35c` | one Organization/claims and the later §10 12-role change |
| `infra/asyncapi/asyncapi.yaml` | `d078f6a7f5c0e644db0e26b3b5c2141100464335` | no new authorised topic contract |

Official external research, retrieved 2026-07-21: [Keycloak Server Administration Guide](https://www.keycloak.org/docs/latest/server_admin/) confirms Organizations are realm state; [realm import/export guidance](https://www.keycloak.org/server/importExport) says Admin Console exports are not backup-grade and boot-time export is recommended. It does not specify this repository's reproducible Organization boot-import representation, so nothing is inferred.

## Candidate inventory and queues

Initial primary audit: EPIC-55/55.2, 55.4, 55.3; EPIC-38/38.1, 38.2; EPIC-40/40.2, 40.3. Reserve audit: EPIC-74/74.1, EPIC-50/50.1, EPIC-74/74.5. The five primary candidates and EPIC-50/50.1 remain blocked as recorded below. Fresh official Keycloak research and a disposable 26.6.4 boot-export/import/token probe changed EPIC-74/74.1 from `SOURCE-BLOCKED` to `READY`; it is now delivered and EPIC-74/74.5 is the primary reserve promotion. No documentation-only work is counted as a story.

## Primary blocker matrix

| Candidate/story | Authoritative statements/facts | Missing semantics/technical choice | Class/final classification | Options and recommendation | Unlocked stories | Exact verification |
|---|---|---|---|---|---|---|
| 55.2, 55.4 | §4.10 names `CUTOFF_REACHED`/`CYCLE_CLOSED`; immutable evidence and port-only reads exist in V52/`CutoffStateReader`; no implicit fallback is frozen. | No primary reason or payment consequence for cutoff/cycle disagreement. | Class C; 55.2 `DECISION-BLOCKED`, 55.4 `CAPABILITY-BLOCKED`. | Cycle-primary, cutoff-primary, explicit primary plus all facts, or ordered stages. Recommend retained facts plus an approved primary/consequence. | 55.2, 55.4. | fixed-clock replay/no-mutation/no-fallback evidence. |
| 55.3, 40.2 | §§3.9/4.10 allow read-only P1 precheck; §4.11 and ADR-N10 preserve LedgerPort and non-guaranteeing reserve. | Account/amount/currency/as-of/result contract and outcome matrix are absent; 35.3 mapping remains blocked. | Class C; `DECISION-BLOCKED`. | narrow ledger read port; direct table read; reserve-as-precheck. Recommend only the first after approval. | 55.3, 40.2. | roles/ownership + no money/reservation/payment mutation + changed-balance proof. |
| 38.1 | §4.11 binds internal book + `NONE_INTERNAL` to internal post/no rail and `ON_INTERNAL_BOOK_POST`; ADR-N10 requires a real post. | Account mapping, eligibility, insufficiency/conflict and payment projection are absent; ADR-N11 is gross only. | Class C; `DECISION-BLOCKED`. | settlement port coordination, approved command functions, or terminal ledger fact then idempotent finality. Recommend terminal fact/projection after business input. | 38.1. | real LedgerPort/replay/conflict/no-partial-state/post-before-finality proof. |
| 38.2 | §4.11 binds file batch + isolated subaccount to file/cycle assignment and `ON_CYCLE_SETTLED`; §4.6 supplies cycles. | Batch creator/identity/membership, cycle selection, close/replay/failure and ingress relationship absent. | Class C; `DECISION-BLOCKED`. | named-cycle assignment, automatic creation, or infer inbound metadata. Recommend named-cycle only after approval. | 38.2. | source-backed runtime assignment/ownership/replay/finality proof. |
| 40.2, 40.3 | §4.11 gives gross reject and broad deferred-next-cycle label; ADR-N13 prohibits automatic next-cycle selection; queue/calendar are P1 deferred. | Eligibility matrix, queue lifecycle/identity, target cycle, calendar/rollover, cancellation/expiry/manual intervention absent. | Class C; 40.2 `DECISION-BLOCKED`, 40.3 `CAPABILITY-BLOCKED`. | reject; queue to named existing target; auto-create/select. Recommend named existing target only after approved policy. | 40.2, 40.3. | fixed-clock matrix plus grants/replay/conflict/no-auto-rollover proof. |

## Independent reserve audit

| Candidate | Classification and evidence |
|---|---|
| EPIC-74/74.1 | **DONE (commit pending):** Official Keycloak documentation gives the supported Organization Membership mapper and its `addOrganizationId`/`addOrganizationAttributes` configuration. A real 26.6.4 boot export proved the import representation (`organizations`, member username records); a fresh import issued a token with `realm_access.roles`, `branch_id`, and nested Organization id/tenant attribute. |
| EPIC-50/50.1 | `CAPABILITY-BLOCKED`: outbound messages and dispatcher claims exist, while retry persistence, DLQ/dead-letter, delivery attempts and receipts do not. A row listing would not satisfy the delivery/retry/DLQ story. |
| EPIC-74/74.5 | **DONE (commit pending):** ADR-N14 selects a dedicated PostgreSQL 18 Compose service and offline Keycloak export. The script stopped/restarted only Keycloak, wrote a `pg_dump` custom archive plus realm files/checksums, restored the archive into an isolated PostgreSQL 18 container (`realm=2`), and parsed the exported `sepa-nexus` Organization realm. |

## Decisions, commits and final evidence

- Class A resolved: the Spring Security JWT converter normalizes the frozen one-Organization Keycloak claim shape into the already-consumed tenant/organization claims, and leaves absent or multi-Organization claims unselected. Class B ADR-N14: dedicated Keycloak PostgreSQL 18 service, offline export alongside the recoverable dump, Keycloak-only maintenance window, checksummed artifacts and restore proof.
- Class C decisions are consolidated in `planning/decisions/DEBINA-WAVE-6-CONSOLIDATED-DECISION-PACKET.md`.
- Story 74.1 production surfaces: `infra/keycloak/realm-export.json` and the JWT claim converter; Story 74.5: `infra/docker-compose.yml`, `infra/scripts/backup-keycloak.sh`, ADR-N14; migrations: none. Runtime evidence: `FullRoleModelRealmTest` (2) imports the actual export into Keycloak 26.6.4, creates a disposable direct-grant probe client, and validates the issued token; `MakerCheckerSeedHygieneTest` (1) validates disjoint submitter/approver members through the admin API; `SecurityConfigTest` (3) proves normalization and ambiguity handling; the fresh Compose backup/restore proof is logged under the configured `/tmp` directory. Kafka/ISO/DB role/RLS evidence: N/A to this identity-only slice. Two full backend regressions are due after this final production change.
- Commits `5e38b5d`, `2355304 feat(security): seed Keycloak organization role model`, and `33e1193 feat(infra): back up dedicated Keycloak state` contain the decision checkpoint and both verified reserve stories. No push occurred.

## Next work

Run final regressions and validators. Then re-audit independent candidates; the five Class C packet decisions still govern cutoff/cycle outcome, ledger precheck, internal book, file batch, and deferred queue.
