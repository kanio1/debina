# Backlog redesign — dual-agent governance and capability DAG session

Session date: 2026-07-16. Baseline: branch `main`, HEAD `0de760d0c6a6324663b8ee5aad91513bc0f5e088`, worktree clean at session start. Mode: `LOCAL-ONLY`, no commits/pushes/fetches performed by this session, no production code or migrations touched — only `AGENTS.md`/`CLAUDE.md` (root + 4 local pairs), `planning/**`, and `tools/agent-config/**`.

This document is the single artifact the session brief asked for in Part G (Część G) — everything else it asked to *check* (instruction audit, backlog inventory, capability graph, cycle detection, hypothesis verification) fed into the edits already applied directly to `AGENTS.md`, `CLAUDE.md`, the four local instruction pairs, and 13 files under `planning/epics/`. This document explains *why* those edits happened and records what was found but deliberately not changed.

## Executive findings

1. **`CLAUDE.md` was badly stale.** It described the repo as "the first commit exists... no application code yet," with "active implementation scope remains EPIC-00." Reality: Iteration 0 (EPIC-00–08) is fully `done`, `backend/` has a working Spring Boot/Modulith app (167/167 tests passing per the last `HANDOFF.md`), `frontend/` has a working Next.js app with real screens, `infra/` runs a live docker-compose stack. Fixed by removing all point-in-time claims from `AGENTS.md`/`CLAUDE.md` entirely and pointing instead at `planning/README.md`, which already carries live status correctly.
2. **Two real execution cycles existed in the capability graph**, both the same shape: a story declares a dependency on an *entire* epic (unqualified) while a story inside that epic depends specifically back on the first story. One was a named hypothesis (H1, `EPIC-31`/`EPIC-43`); the other (`EPIC-14`/`EPIC-39`) was found incidentally while building `planning/capability-graph.json` and is not one of the ten named hypotheses. Both are now fixed and verified cycle-free by `tools/agent-config/validate-capability-graph.py`.
3. **All ten named hypotheses (H1–H10) were confirmed** by direct evidence in the epic files (see "Hypothesis verification" below) — none were refuted. Several fixes discovered along the way went beyond the ten (a third dependency-narrowing, `EPIC-27` on `EPIC-26`, turned out to make an entire not-started epic `READY` today; a genuinely missing epic-owner for "build the GraphQL layer" surfaced from H8's investigation).
4. **`EPIC-11` is the only currently `in-progress` epic (of nine) with a real actionable next step.** The other eight are blocked on decisions, missing capabilities in other modules, or (uniquely for `EPIC-10`) a pending human decision — see H10.
5. **The backlog was not under-specified — it was over-specified at the wrong granularity in a systematic way.** ~19 individual stories across the corpus reference a sibling epic at story level (correctly narrow) while their *own* epic's frontmatter still declares the dependency at whole-epic level (too broad) or omits it. This is a single recurring defect class, not 19 unrelated ones.

## Agent instruction audit

Full classification table and per-rule reasoning: see the session's working notes (not checked into the repo — ephemeral scratch, per this repo's own memory-hygiene convention of not persisting what's derivable from current file state). Summary of what changed:

- Root `AGENTS.md` rebuilt: purpose statement, the "Kim jesteś" supreme rule (previously **Claude-only**, now shared — Codex sessions never saw "jedynym źródłem prawdy jest dokumentacja" before this session), reading-order table, frozen-architecture summary (cross-referencing `README.md`'s ADR table instead of duplicating its 8-row title list), capability-first planning principle (new), session protocol (consolidated from 3 near-duplicate copies across `AGENTS.md`+`CLAUDE.md` into 1), git/worktree safety, "what not to do," and a routing table to the 4 new local `AGENTS.md` files.
- Root `CLAUDE.md` rebuilt to the brief's exact template: `@AGENTS.md` import + 4 short Claude-Code-only bullets (skill routing, `DOCKER_HOST` mechanism, explore-then-plan for risky changes, guardrails-belong-in-settings-not-prose).
- `backend/AGENTS.md`, `frontend/AGENTS.md`, `infra/AGENTS.md`, `planning/AGENTS.md` created (none existed before). Each paired with a one-line `@AGENTS.md` `CLAUDE.md`.
- **Fixed a real, self-contradictory instruction**: `AGENTS.md` said both "run `npm run build && npm run lint`" *and* "prefer pnpm... otherwise npm" in the same file, while `frontend/` has only committed `pnpm-lock.yaml` (no `package-lock.json`) — `npm run build` would have generated a second, competing lockfile. `frontend/AGENTS.md` now says pnpm unconditionally, no fallback.
- **Fixed a dead link**: `AGENTS.md` referenced `infra/postgres/README.md`, which does not exist (`infra/` only has `asyncapi/`, `keycloak/`, `docker-compose.yml`, `README.md`). `infra/AGENTS.md` now points at the real source (`backend/src/main/resources/db/migration/`).
- **Fixed a stale Playwright gate**: both files said "This is Iteration 0, no Playwright" — Iteration 0 finished at EPIC-08. The real, current gate (per `planning/README.md`'s own `[SEKWENCJONOWANIE — NIE ŁAMAĆ]` note in `EPIC-24`) is capability-based: Playwright waits on the first 3 screens (Control Room + Payments&Files + Payment Detail/Timeline), not on an iteration number. `planning/AGENTS.md` now states the capability-based gate and explicitly warns not to re-derive it from the iteration number.
- Verified the Codex skill fallback (`tools/codex/.agents/skills` → symlink to `.claude/skills`) is real and working, not stale — no fix needed there.
- New validator: `tools/agent-config/validate-agent-instructions.sh` — checks the `@AGENTS.md` import, the 4 named stale-statement patterns, npm/pnpm conflicts (with a prohibition-aware grep so "never run npm" doesn't self-flag), reports file sizes, checks for stray `AGENTS.override.md`, and checks the 4 local wrapper pairs exist. Exits non-zero on any violation. **Currently: PASS.**

## Capability inventory

Full per-epic/per-story inventory (66 of 76 epics; EPIC-00–09 excluded as already covered by `planning/README.md`'s table) was built as a research pass and is summarized here; the durable artifacts are `planning/capabilities.yaml` (human-readable registry, 12 deep-dive epics at full story granularity + dependency-narrowing/cross-link findings) and `planning/capability-graph.json` (machine graph: 142 nodes, 251 edges after this session's fixes, validated cycle-free).

Corpus totals (unchanged by this session): **76 epics, 266 stories, 319 tasks** before splits; splits add 1 epic-unchanged/13 net new story records across 7 files (see mapping table).

## Cycles

### `[CYCLE-DETECTED]` 1 — `cycle.signature-egress` (H1)

```
EPIC-31/Story 31.3 --[depends on WHOLE EPIC-43]--> EPIC-43
EPIC-43 --[rolls up to]--> EPIC-43/Story 43.2
EPIC-43/Story 43.2 --[depends on]--> EPIC-31/Story 31.3
```

Root cause: `31.3`'s `depends_on` named the entire `EPIC-43` epic instead of the one story it actually blocks on. The exact same defect shape (`31.2` ↔ `EPIC-19`/`19.2`) was already found and fixed once in this same file, by re-granulating to story level — this pair simply wasn't given the same treatment in that earlier pass.

**Fix applied**: `Story 31.3` split into `Story 31.3A` (standalone `SignatureSigningPort` capability — round-trip sign→verify via a stub caller, depends only on `31.1`, no `EPIC-43` dependency). The story's own original description already implied this split (it separately named "signer-stub through the port, round-trip sign→verify" and "wywoływany z egress" as two things). The egress-invocation detail moved into `EPIC-43`'s `Story 43.2`, which already independently described the same invocation — the two stories were duplicating one integration point, not just cyclically depending on each other. `43.2` now depends on `31.3A`.

### `[CYCLE-DETECTED]` 2 — `cycle.egress-transport-status-vs-finality` (found incidentally, not a named hypothesis)

```
EPIC-14/Story 14.3 --[depends on WHOLE EPIC-39]--> EPIC-39
EPIC-39 --[rolls up to]--> EPIC-39/Story 39.4
EPIC-39/Story 39.4 --[depends on]--> EPIC-14/Story 14.3
```

Root cause: identical shape to H1. Additionally: `14.3` and `39.4` turn out to assert the *exact same thing* — both verify `DeliveredNotFinalTest`, and `EPIC-39`'s own file already marks it "współdzielony z EPIC-14." This is the same pattern as other explicitly-shared tests already documented elsewhere in this corpus (`CycleCloseRaceTest` shared between `EPIC-34`/`EPIC-37`, `SignatureBeforeParseOrderingTest` shared between `EPIC-19`/`EPIC-31`) — a shared test implementation is not supposed to imply a blocking dependency in either direction.

**Fix applied**: `14.3` now depends on `EPIC-39/Story 39.2` specifically (`FinalityPolicy` + `settlement_finality_records` — the actual capability its assertion reads). `39.4` no longer references `EPIC-14/14.3` as a dependency at all; both stories now carry a "shared test, either side may build it first" note instead.

Both cycles verified resolved: `python3 tools/agent-config/validate-capability-graph.py` → `RESULT: PASS`, 0 cycles, on the post-fix `planning/capability-graph.json` (142 nodes / 251 edges).

### A lesson the fixes themselves demonstrate

While modeling the `EPIC-14`/`EPIC-46` "transport_attempts" hidden-blocker finding (below) at whole-epic granularity, the graph-builder script *manufactured a third, artificial cycle* that does not actually exist in the corpus — purely because whole-epic references are transitive through everything else that epic contains. Re-modeling the same two real facts (`14.1` needs something `EPIC-46` produces; `EPIC-46`'s `46.3` needs `EPIC-14`'s `14.2`) at story granularity made the artificial cycle disappear without changing either underlying fact. This is not a hypothetical caution — it happened during this session's own tooling work, and is direct, first-hand evidence for why `planning/AGENTS.md`'s "capability-first, narrow dependency" rule exists: **broad epic-level dependencies aren't just imprecise, they can silently manufacture false cycles that a correctly-scoped dependency would never create.**

## Hidden blockers

- **`EPIC-14` Story 14.1** requires a grant-test proving `egress` is the sole writer of `outbound_messages`/`transport_attempts`/`delivery_receipts` — but `transport_attempts` is only ever created by `EPIC-46` Story 46.1, and `EPIC-14`'s only declared epic-level dependency is `EPIC-43` (which does not include `EPIC-46`). As written, this story could show `done` once `EPIC-43` lands even though one of the three tables its own task names doesn't exist yet. **Not fixed this session** (out of the 12 deep-dived epics; flagged as a `dependency_granularity_candidate` in `planning/capabilities.yaml` with the exact narrow fix — `14.1` should depend on `EPIC-46` specifically, not be added to `EPIC-14`'s epic-level `depends_on` at whole-epic granularity, which the lesson above shows would recreate a cycle).
- **No epic owns building the GraphQL layer itself.** Surfaced while verifying H8. `EPIC-16` only covers *ownership enforcement* of a GraphQL layer once one exists (grants/RLS on read models); `EPIC-23` Story 23.1B only covers *codegen* once a schema exists. Nothing currently specifies who writes the first `spring-graphql` schema/resolver — and three separate blocked stories (`EPIC-26`/26.4, `EPIC-23`/23.1B, `EPIC-16`'s own stories) all silently assume it will eventually exist. Recorded as `[OPEN-QUESTION]` in `EPIC-26`'s file; **not resolved here** per this repo's "don't invent architecture" rule — needs a user/team decision on which epic (existing or new) owns it.

## Overbroad dependencies (narrowed this session)

| Epic/Story | Was | Now | Evidence |
|---|---|---|---|
| `EPIC-35`/`35.1` (+ epic-level) | `EPIC-12` (whole) | `EPIC-12`/`Story 12.1` | H2 — no story body ever named which part of `EPIC-12` was needed; `12.2` is `[NO-CODE]` until Iteration 5 and unrelated to strategy resolution |
| `EPIC-20`/`20.3` | `EPIC-26` (whole, and the story's own note incorrectly still claimed `EPIC-26` was `not-started`) | `EPIC-27`/`Story 27.2` | H3 — `EPIC-26`'s own file already explicitly diagnosed this exact mismatch in prose without propagating the frontmatter fix |
| `EPIC-27` (epic-level) | `EPIC-26` (whole) | `EPIC-26`/`Story 26.3` | Found while building the graph (not a named hypothesis) — `EPIC-26` reads as blocked only because of the unrelated `26.4` GraphQL story; the correlation engine only ever needed `26.1`–`26.3`, done since 2026-07-15. **This makes `EPIC-27` READY today.** |
| `EPIC-31`/`31.3`→`31.3A` | `EPIC-43` (whole) | (none — the egress dependency was removed entirely) | H1 / cycle fix |
| `EPIC-43`/`43.2` | `EPIC-31`/`31.3` (which cycled back) | `EPIC-31`/`31.3A` | H1 / cycle fix |
| `EPIC-14`/`14.3` | `EPIC-39` (whole) | `EPIC-39`/`39.2` | second cycle fix |
| `EPIC-39`/`39.4` | `EPIC-14`/`14.3` (back-reference) | (removed — shared-test note instead) | second cycle fix |

**Flagged as candidates but *not* changed this session** (same pattern, not evidenced enough within this session's scope to safely narrow — recorded in `planning/capabilities.yaml`'s `dependency_granularity_candidates`, not silently dropped):

- `EPIC-51`, `EPIC-57` — same un-narrowed `EPIC-12` (whole) pattern as `EPIC-35`; likely also only need `Story 12.1`, not confirmed by a deep-dive of either file this session.
- `EPIC-65` — depends on the whole `EPIC-27` (including the `[P1]` manual-correlation half, `27.5`) when its own `Story 65.4` ("Konsumpcja `iso.message.correlated`") looks narrower.
- `EPIC-24`/`24.7` (Reference Data / Admin workspace) — the inventory flagged this as "closest to unblockable" of the remaining workspaces (`EPIC-12`/`12.1` is `done`, only CRUD write endpoints remain), but its `depends_on` is still the whole `EPIC-12`. Not touched — `24.7` was not one of the deep-dived stories inside `EPIC-24` (only `24.2` was split this session).
- `EPIC-14`/`14.1` → `EPIC-46` (the transport_attempts hidden blocker above) — the fix is known (add a narrow story-level edge, not a whole-epic one) but not applied to the file this session.

## Mixed-phase / mixed-owner / bundled stories (split this session)

| Hypothesis | Epic/Story | Bundled concerns | Split into |
|---|---|---|---|
| H9 | `EPIC-10`/`10.1` | technical proof + user decision + command-API design + DB role/function design + production migration + `JSON_DIRECT` integration + `pain.001` integration + privilege/search_path/rollback verification | `10.1A` (decision gate) / `10.1B` (API+role+migration) / `10.1C` (`JSON_DIRECT`) / `10.1D` (`pain.001`) / `10.1E` (verification — **already done**, 27 passing tests, previously hidden inside a `blocked` story) |
| H4 | `EPIC-24`/`24.2` | feature implementation (done) + Playwright acceptance (blocked on sequencing) | `24.2A` (feature — **done**) / `24.2B` (Playwright — blocked, unchanged gate) |
| H7 | `EPIC-23`/`23.1` | REST/OpenAPI codegen + GraphQL SDL codegen, two independent capability gaps | `23.1A` (REST) / `23.1B` (GraphQL) |
| H5 | `EPIC-25`/`25.3` task 2 | retry metric + DLQ depth + header propagation + DLQ alert, four concerns behind one `verify:` | `25.3A` (lag — unchanged, already done) / `25.3B` (retry) / `25.3C` (DLQ depth) / `25.3D` (headers) / `25.3E` (alert) |
| H6 | `EPIC-27`/`27.4` | `[MVP]` DLQ write + `[P1]` manual-correlation operator action, split mid-sentence by priority tag | `27.4` (`[MVP]` DLQ only) / `27.5` (`[P1]` operator read model) |

Every split preserves the original story number as a prefix (per the session brief's own instruction), keeps `Story N.M`/`Story N.O` numbering elsewhere in the same file unchanged, and records a `[SPLIT 2026-07-16 ...]` note in the epic file itself explaining the "was" state and rationale — the split is traceable from the file alone, not only from this document.

**H8** (`EPIC-26`/`26.4` belongs to a GraphQL/frontend concern, not core lineage-recording) was confirmed but **not moved** — relocating a story across epic files is a bigger structural change than a same-file split, and doing it well requires first resolving the "no epic owns the GraphQL layer" open question above. Recorded as a note in `26.4` itself instead.

## Decision gates

| Gate | Blocks | Requires | Status |
|---|---|---|---|
| `gate.EPIC-10.10_1.security-definer` | `10.1A`–`10.1D`, `10.2` | User acceptance of the `SECURITY DEFINER` recommendation in `/EPIC-10-transaction-coordination-decision-memo.md` (or a request for further proof / an alternative) | **OPEN** |

This is the only formally modeled decision gate this session — it's also the only one found where a story is blocked purely on a pending human choice rather than a missing capability (see H10 table below; every other blocked-in-progress epic is capability- or decision-*adjacent*-blocked, not decision-gated in this narrow sense).

## Capability DAG

`planning/capability-graph.json` (142 nodes, 251 edges) + `planning/capability-graph.mmd` (curated visual subgraph — phase heads, both cycle fixes, the one decision gate) + `planning/capabilities.yaml` (human-readable registry and rationale). Validated cycle-free by `tools/agent-config/validate-capability-graph.py` after this session's fixes; was 2-cycles-`FAIL` before them (both cycles above).

Not attempted: a full story-level breakdown for all 76 epics (Part 14's instruction not to "add capability fields to all 76 epics for cosmetic reasons" was followed literally — the 12 deep-dived epics plus the handful of individually-evidenced `EPIC-14`/`39`/`45`/`46` stories have real story nodes; the other 60 keep epic-level granularity, matching what `planning/README.md` already tracked and what the inventory confirmed has zero epic-level/frontmatter mismatches).

## Ready queue (top 10, hard-readiness-gated)

Gate applied per Part 21: no unresolved decision, no source conflict, no missing capability, executable `verify:`, clear owner, no hidden cross-module design question. Ranked by MVP critical-path value and fan-out (how much downstream work each unlocks), not strictly by epic number.

1. **`EPIC-27`/`Story 27.1`** — pacs.002 identifier extraction. `depends_on: []`. Newly `READY` (see "narrowed dependencies" above — the epic-level block was an artifact of `26.4`, not a real gap). Unlocks the entire correlation-engine chain (`27.2`→`27.3`/`27.4`/`27.5`), which in turn unblocks `EPIC-20`/`20.3` and is a hard prerequisite for `EPIC-65` (Case module).
2. **`EPIC-11`/`Story 11.2`** — depends on `11.1` (`done`). The one epic H10 found genuinely unblocked among the nine `in-progress` epics.
3. **`EPIC-11`/`Story 11.3`** — depends on `EPIC-09`/`Story 9.4` (`done`).
4. **`EPIC-35`/`Story 35.1`** — `SettlementStrategyResolver`. Newly `READY` after this session's H2 narrowing (`EPIC-12`/`12.1` is `done`). Entry point to the entire Settlement phase (Faza 5) — `EPIC-33`, `34`, `36`–`42` all transitively wait on this.
5. **`EPIC-32`** (Ledger core) — `depends_on: [EPIC-09]` (`done`), zero narrowing needed, zero code today. Highest-fan-out `READY` epic in the corpus: `EPIC-13`, `33`, `34`, `36` all name it directly.
6. **`EPIC-43`/`Story 43.1`** — `outbound_messages` + `SKIP LOCKED` dispatcher. `depends_on: []`. Entry point to the entire Egress phase (Faza 6).
7. **`EPIC-31`/`Story 31.3A`** — standalone signing capability, `depends_on: [31.1]` (`done`). Newly `READY` as a direct result of this session's H1 cycle fix — was structurally un-startable before today.
8. **`EPIC-73`** (Ingress file rail) — `depends_on: [EPIC-19]` (`done`), no blocking notes found in the lighter-pass inventory.
9. **`EPIC-10`/`Story 10.1A`** — not a coding task, but the single highest-leverage item in the whole backlog: accepting (or explicitly redirecting) the `SECURITY DEFINER` recommendation unblocks `10.1B`–`10.1D`, `10.2`, and (per the decision memo) the `ingress.idempotency_keys` generalization question too.
10. **`EPIC-25`/`Story 25.3B`** (retry counter) — depends only on `25.3A` (`done`); smallest-scope newly-actionable item from the H5 split, good for a short session.

Not included despite looking tempting: `EPIC-24`/`24.7` and `EPIC-51`/`EPIC-57` — all three are *candidates* for the same kind of narrowing that made #1 and #4 above `READY`, but weren't verified deeply enough this session to promote without risk (see "Overbroad dependencies... flagged but not changed").

## Execution waves

| Wave | Focus | Entry points (from Ready queue) | Stop condition |
|---|---|---|---|
| **Wave 0** (this session) | Agent governance, planning structure, decision gates | — | Both cycles fixed, validators pass, `HANDOFF.md` written |
| **Wave 1** | Payment/ISO spine completion | `27.1`→`27.2`→`27.3`/`27.4`/`27.5`; then `20.3` (now unblockable once `27.2` lands); `11.2`/`11.3` | ISO correlation engine `done`, `EPIC-20` fully `done` |
| **Wave 2** | API contracts and testability | `EPIC-23`/`23.1A` (once any backend REST module exposes OpenAPI — not yet true, watch for it as a side effect of Wave 1/3 work) | REST codegen wired; GraphQL codegen (`23.1B`) stays blocked until the open GraphQL-owner question resolves |
| **Wave 3A** | Ledger/Settlement | `EPIC-32`, `35.1`→`35.2`/`35.3`, then `EPIC-13` (ledger ownership) | `LedgerPort` exists, strategy resolver frozen-invariant-tested |
| **Wave 3B** | Signature/Egress | `31.3A` (already `done`-able independent of egress), `43.1`→`43.2` (now correctly depends on `31.3A`)→`43.3`/`43.4`/`43.5` | Egress rail dispatches, real signing wired |
| **Wave 4** | Routing/Reconciliation | `EPIC-51` (after narrowing its `EPIC-12` dependency, mirroring `35.1`'s fix), `EPIC-57` (same) | Both resolvers exist |
| **Wave 5** | Case/Simulation/advanced UI | `EPIC-65` (once `27.2`+ lands), `EPIC-24`/`24.3`–`24.9` (each individually, as their backend deps land) | First non-Payments&Files workspace ships |

One vertical slice per session remains the operative discipline (per this session's own governance principles) — the waves above are a sequencing map, not a mandate to attempt multiple in parallel within one session.

## Old-to-new mapping

| Old | Problem | New | Files touched |
|---|---|---|---|
| `EPIC-10`/`Story 10.1` | H9 — 8 concerns bundled | `10.1A` (decision) / `10.1B` (API+role+migration) / `10.1C` (JSON_DIRECT) / `10.1D` (pain.001) / `10.1E` (verification, done) | `EPIC-10-iso-lineage-ownership.md` |
| `EPIC-10`/`Story 10.2` depends_on | pointed at whole `10.1` | now `10.1B` | same file |
| `EPIC-20`/`Story 20.3` depends_on | H3 — stale/wrong target | `EPIC-27`/`Story 27.2` | `EPIC-20-payment-lifecycle-fsm.md` |
| `EPIC-23`/`Story 23.1` | H7 — REST+GraphQL codegen bundled | `23.1A` (REST) / `23.1B` (GraphQL) | `EPIC-23-frontend-foundation.md` |
| `EPIC-24`/`Story 24.2` | H4 — feature + Playwright bundled | `24.2A` (feature, done) / `24.2B` (Playwright, blocked) | `EPIC-24-frontend-screens.md` |
| `EPIC-25`/`Story 25.3` task 2 | H5 — 4 concerns bundled | `25.3A` (lag, unchanged) / `25.3B`/`25.3C`/`25.3D`/`25.3E` | `EPIC-25-observability-consolidation.md` |
| `EPIC-25`/`Story 25.4` depends_on | pointed at whole `25.2`+`25.3` | now `25.2`+`25.3D`+`25.3E` | same file |
| `EPIC-26` (epic-level) depends_on of `EPIC-27` | over-broad, hid a `READY` epic | `EPIC-26`/`Story 26.3` | `EPIC-27-iso-correlation-engine.md` |
| `EPIC-27`/`Story 27.4` | H6 — MVP+P1 bundled | `27.4` (MVP only) / `27.5` (new, P1) | `EPIC-27-iso-correlation-engine.md` |
| `EPIC-31`/`Story 31.3` | H1 — cycle with `EPIC-43` | `31.3A` (standalone, no `EPIC-43` dep) | `EPIC-31-signature-module.md` |
| `EPIC-43`/`Story 43.2` depends_on | pointed at cyclic `31.3` | now `31.3A` | `EPIC-43-egress-rail-outbound-dispatch.md` |
| `EPIC-35`/`35.1` (+ epic-level) depends_on | H2 — whole `EPIC-12` | `EPIC-12`/`Story 12.1` | `EPIC-35-settlement-strategy-resolver.md` |
| `EPIC-14`/`Story 14.3` depends_on | cycle with `EPIC-39` | `EPIC-39`/`Story 39.2` | `EPIC-14-egress-boundary-ownership.md` |
| `EPIC-39`/`Story 39.4` depends_on | cycle with `EPIC-14` | removed (shared-test note) | `EPIC-39-settlement-finality-model.md` |
| `EPIC-45`/`Story 45.2` depends_on | dangling reference to removed `31.3` | `31.3A` | `EPIC-45-egress-outbound-message-lifecycle.md` |
| `EPIC-26`/`Story 26.4` | H8 — GraphQL concern in a lineage-write epic | unchanged location, `[OPEN-QUESTION]` note added (no epic owns building GraphQL) | `EPIC-26-iso-message-lineage-core.md` |

13 epic files touched total: `EPIC-10`, `14`, `20`, `23`, `24`, `25`, `26`, `27`, `31`, `35`, `39`, `43`, `45`. `EPIC-12`/`28` (also named in the session brief's "primarily" list) were read in full and cross-checked but needed no edits — their dependency declarations were already correctly narrowed (`28.2`→`12.2` specifically is a positive counter-example already in the corpus).

## Changes rejected as speculative

- **Did not** invent `27.6`/`27.7` numbering or a broader `27.1`–`27.7` pipeline restructure as the session brief's own Part 20 example speculatively suggested — the real corpus only had 4 stories (`27.1`–`27.4`) before this session; the new `27.5` uses the next real available number, not a renumbering that would imply stories that don't exist.
- **Did not** split `EPIC-23`/`23.1` into 5 sub-parts (`23.1A`–`23.1E`) as one illustrative brief example proposed — the real story is a single codegen-wiring task with exactly two bundled concerns (REST, GraphQL); there is no separate pre-existing "source" story to split from "codegen," so a 2-way split is what the evidence supports.
- **Did not** relocate `EPIC-26`/`26.4` into another epic file (H8) — confirmed the concern is misplaced, but moving it requires first resolving the open "who builds GraphQL" question; recorded as an open question instead of guessing an answer.
- **Did not** apply the `EPIC-51`/`EPIC-57`/`EPIC-65`/`EPIC-14`-14.1/`EPIC-24`-24.7 narrowings identified as candidates — same defect pattern as fixes that *were* applied, but these five epics were not deep-dived this session and narrowing them without reading their full story text would be exactly the kind of unverified inference this session's own methodology argues against.
- **Did not** touch the `npm run test:e2e` verify-command convention used consistently across `EPIC-24`/`29`/`48`/`49`/`50`/`52`/`56`/`63`/`64`/`72`/`73` (should eventually read `pnpm run test:e2e` once Playwright is actually scaffolded, per the corrected `frontend/AGENTS.md`) — flagged here, not mass-edited, per the explicit "no cosmetic sweep across all 76 epics" instruction.
- **Did not** build a Markov/telemetry-based prioritization model — per the session brief's own Part 16, there isn't yet enough historical transition data (`READY→IN-PROGRESS→DONE`/`BLOCKED`/`REWORK`) in this repo to fit one meaningfully. A future-telemetry schema proposal was in scope per the brief but was judged lower value than finishing the graph/split work within this session; not built. If a future session wants it, the fields needed are: `story_id`, `started_at`, `blocked_at`, `unblocked_at`, `completed_at`, `blocker_type`, `files_changed`, `modules_touched`, `targeted_test_result`, `regression_result`, `rework_count`, `decision_wait_time` — matching the brief's own Part 16 list.
- **Did not** implement any new deterministic guardrail hooks — audited `.claude/settings.local.json` and found the git/podman-safety guardrails this session's brief worried about (`git push`/`reset --hard`/`clean`, `podman volume rm`/`prune`, `podman system prune`, `rm -rf /`/`~`) are already hard-`deny`d there, and the risky-but-sometimes-needed ones (`git add`/`commit`/`checkout`, `podman compose down`, `rm`, `chmod`) are already `ask`-gated. Nothing to add.
