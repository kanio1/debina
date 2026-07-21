# Debina Wave 6 consolidated decision packet

The following answers cannot be inferred. Recommendations are not approvals.

## D6-01 — Cutoff and runtime-cycle disagreement

**Affected stories:** EPIC-55/55.2, 55.4.  **Business problem:** a route can observe a passed configured cutoff and a non-open runtime cycle.

**Why blocked:** blueprint §4.10 names both outcomes but not precedence or consequence. **Sources examined:** message-flow §§3.9, 4.6, 4.10, 4.11, §8; ADR-N2/N13; decision gate. **Sources say:** immutable explanation, port-only reads, no implicit fallback. **They do not say:** primary outcome or reject/hold/alternate-profile/alternate-cycle action.

**Options:** cycle-primary; cutoff-primary; preserve both facts with explicit primary; source-defined stages. **Recommendation:** preserve both facts, but choose no primary/consequence until authorised. **Risk:** wrong choice creates an unsupported rejection or hold.

**Exact answer required:** for every simultaneous/discordant cutoff-cycle combination, name the primary route code and payment consequence; state whether named alternate cycle/profile use is allowed. **Unlocked:** 55.2/55.4. **First action:** fixed-clock outcome/replay/no-mutation tests and immutable evidence implementation.

## D6-02 — Liquidity precheck request/result/consequence

**Affected stories:** EPIC-55/55.3, EPIC-40/40.2. **Why blocked:** source lacks account selection, amount/currency, as-of, result vocabulary and outcome matrix.

**Sources examined:** message-flow §§3.9, 4.10, 4.11, 4.13; ADR-N10; EPIC-35/35.3; V8/V9. **Sources say:** routing never reads ledger tables, precheck is read-only/P1 and a later reserve can fail. **They do not say:** participant/account mapping, absent-account/unknown semantics or business action.

**Options:** narrow ledger read port; direct ledger query; use reserve as precheck. **Recommendation:** narrow ledger-owned, explicitly non-guaranteeing read port only after business inputs/results are approved. **Risk:** guessed mapping/guarantee changes payment behavior and violates ownership.

**Exact answer required:** request fields, `AVAILABLE`/`INSUFFICIENT`/`UNKNOWN`, as-of semantics, absent-account action and supported pair/mode/cutoff outcome matrix. **Unlocked:** 55.3/40.2 (then perhaps 40.3). **First action:** PostgreSQL read-boundary/no-money proof and port implementation.

## D6-03 — Internal-book account and outcome authority

**Affected stories:** EPIC-38/38.1. **Why blocked:** internal post/finality is source-backed but account mapping, eligibility, failure and projection behavior are not.

**Sources examined:** message-flow §§4.5, 4.11, §8; ADR-N10/N11; participant-account DDL. **Sources say:** LedgerPort-only movement, no external rail, finality after real post. **They do not say:** debtor/creditor account/currency mapping, reserve requirement, insufficiency/conflict or customer status.

**Options:** settlement port coordinator; approved command functions; terminal ledger fact then idempotent finality projection. **Recommendation:** terminal fact/projection after business inputs, retaining owner ports and not extending ADR-N11 by implication. **Risk:** wrong mapping can book money incorrectly.

**Exact answer required:** eligible scope, account/currency mapping, reserve requirement, failure result and authorised payment projection. **Unlocked:** 38.1. **First action:** if necessary technical ADR, then LedgerPort/replay/conflict/no-partial-state/post-before-finality proof.

## D6-04 — File-batch assignment contract

**Affected stories:** EPIC-38/38.2. **Why blocked:** file/cycle assignment is stated, but the batch aggregate is not.

**Sources examined:** message-flow §§4.6, 4.11, §7, §8; ADR-N13; EPIC-73. **Sources say:** typed pair, later cycle finality. **They do not say:** batch owner/identity/creator/membership, cycle/date/session, close/replay/failure or relation to inbound files.

**Options:** explicit named-cycle assignment; automatic cycle creation; infer inbound metadata. **Recommendation:** explicit named-cycle assignment after contract approval. **Risk:** conflating settlement and file rails invents replay/finality policy.

**Exact answer required:** batch identity/owner/creator, profile/cycle/date/session membership and close rules, restrictions, replay/conflict and file-evidence relationship. **Unlocked:** 38.2. **First action:** source-backed assignment runtime proof; EPIC-73 stays separately gated.

## D6-05 — Deferred-insufficiency queue and target-cycle policy

**Affected stories:** EPIC-40/40.2, 40.3. **Why blocked:** queue/retry/calendar are P1 deferred and next-cycle behavior is incomplete.

**Sources examined:** message-flow §§4.6, 4.11, §8; ADR-N13; decision gate; review §§19--20. **Sources say:** gross rejects, deferred may move to next cycle, no automatic cycle selection. **They do not say:** matrix, queue lifecycle, target selection, calendar/time zone, cancellation/expiry/manual intervention.

**Options:** reject; queue to explicitly supplied existing target; automatic next-cycle creation/selection. **Recommendation:** explicit existing target only after business matrix approval. **Risk:** automatic rollover can settle on the wrong date/session or hold indefinitely.

**Exact answer required:** supported matrix; queue owner/identity/timestamps/priority; target source; cancellation/expiry/manual intervention; replay/conflict and calendar/time-zone rules. **Unlocked:** 40.2/40.3. **First action:** technical representation ADR only if needed, then migration with fresh/upgrade/grant/replay proof.
