# SEPA Nexus — Playwright Test Learning Business Development

**Nature.** An architectural review and *functional* redesign pass answering one question: does SEPA Nexus have a rich, realistic, business-justified frontend to teach standard-through-advanced Playwright 1.61, without building an artificial test playground? Every proposed feature must carry business sense, a concrete Playwright learning value, a home in an existing workspace, a testable state, a user role, test data, and a flakiness-control story — simultaneously, not any one alone. `[NO-CODE]` — no tests, no React code, no new frontend design from scratch. Domain untouched.
**Sources reviewed:** the frontend blueprint, the three nine-screen UI specs, the component-foundation blueprint, both Keycloak blueprints, the message-flow/data blueprint, ADR-N3/N4.
**Playwright verification (July 2026):** 1.61 is current stable (June 15 2026) — headline features directly relevant here: **`page.localStorage()`/`page.sessionStorage()`** as first-class APIs (no more manual `evaluate()` round-trips for the no-browser-token test), the **WebAuthn virtual authenticator** (passkey flows testable in CI without real hardware), `expect.soft.poll()` for eventually-consistent assertions, WebSocket traffic now visible in trace/HAR. Carried forward from 1.59/1.60: ARIA snapshots (`toMatchAriaSnapshot`), `tracing.startHar()`, ARIA-first locators. Network interception (`page.route`/`route.fulfill`/`route.continue`/`route.abort`/`route.fetch`) is mature and best used **narrowly and late** (register before navigation, scope patterns tightly, prefer `route.fallback()` for chaining, avoid mocking the whole backend).

---

## 1. Executive Verdict

```text
VERDICT: PARTIALLY READY
```

`[TESTABILITY]` The existing design is **already unusually rich** for a test-learning surface: BFF session model, GraphQL-read/REST-command split, SSE with reconnect/backoff, per-schema data with 11 real roles, four-axis status separation, drawers/tabs/dialogs/toasts, URL-driven table state, a reusable four-state component family, an axe-core gate, and — uniquely valuable — a **deterministic simulation engine that generates test data through real business paths** (ADR-N7 `JSON_DIRECT`, seeded scenarios, replay). Most of the standard-to-advanced Playwright surface (locators, assertions, tables, tabs, drawers, dialogs, toasts, auth/session, multi-role, SSE, accessibility, fixtures, parallel execution) is **already covered by business-real features**, not bolted on.

Two things are genuinely thin, and both are legitimate business gaps, not test-only additions: **(1)** file upload is named ("Upload file") but not designed to the depth that teaches upload/progress/validation Playwright patterns; **(2)** the Reconciliation work queue has no designed behaviour for the very real production hazard of **two operators racing to assign the same exception** — a stale-conflict/optimistic-locking teaching surface that the domain already implies but never specifies. A third area, downloadable artifacts (delivery receipts), is under-specified relative to its audit value. These three are `[ADD]` below — each with a real payments-operations reason, not manufactured for Playwright's sake.

Nothing needs to be **rejected from the existing specs** — the design discipline (no optimistic UI, no `waitForTimeout`, empty≠unauthorized, real roles) already excludes the artificial patterns that would otherwise creep in. §10 rejects patterns that *could* be proposed but must not be.

---

## 2. Playwright Learning Coverage Matrix

| Playwright Area | Current Coverage | Missing Surface | Business Justification | Priority |
|---|---|---|---|---|
| Role/ARIA locators | Strong — `getByRole`/`getByLabel` implied by real `<table>`, shadcn primitives, ARIA tablist/dialog | none | accessible operator console | `[MVP]` |
| Labels | Strong — every control has an English label in the specs | none | operator usability = test usability | `[MVP]` |
| Stable `data-testid` | Strong — frozen convention, vendored components | none | testability by design | `[MVP]` |
| Auto-waiting/actionability | Strong — disabled/enabled command states (Close cycle, Retry) are real domain states | none | commands are genuinely conditional | `[MVP]` |
| Assertions (incl. `toMatchAriaSnapshot`) | Adequate — status chips, empty/error/unauthorized states are assertable | ARIA snapshot baselines not yet named | operator screens are exactly what a11y snapshots protect | `[MVP]` |
| Tables (TanStack, real `<table>`) | Strong — every list/queue screen | none | data-dense operator work | `[MVP]` |
| Filters / URL state | Strong — frozen URL-driven table state (foundation §11d) | none | shareable investigation links are a real operator need | `[MVP]` |
| Pagination | Adequate — implied by `EntityTable` | not explicitly walked in any spec | large payment volumes are realistic | `[MVP]` |
| Sorting | Adequate — `aria-sort` frozen | not explicitly walked | operators sort by age/severity routinely | `[MVP]` |
| Tabs | Strong — every object detail page | none | object-centered IA *is* tabs | `[MVP]` |
| Drawers/sheets | Strong — Evidence drawer, shadcn `sheet` | none | evidence-on-demand is core UX | `[MVP]` |
| Dialogs (confirm) | Strong — every destructive/state-changing command | none | real financial/operational consequence | `[MVP]` |
| Toasts | Strong — every command success/failure | none | audit-consequence surfacing | `[MVP]` |
| Uploads | **Partial** — "Upload file" named, not designed to depth | drag-drop zone, progress, client+server invalid-file validation | file rail is a real ingress channel | `[ADD]`, `[MVP]` |
| Downloads | **Partial** — evidence bundle is `[P1]`, receipts are "view" not "download" | explicit download surface + assertion pattern | receipts/evidence are audit artifacts operators export | `[ADD]`, `[P1]` |
| API testing (`APIRequestContext`) | Adequate — BFF REST commands are a natural direct-API layer | not named as a layered strategy | fast, UI-independent contract checks | `[ADD]` (test-architecture, not UI) |
| BFF command testing | Strong — every command is REST-through-BFF by design | none | ADR-N3 | `[MVP]` |
| GraphQL read verification | Adequate — read-only, inspectable via `waitForResponse` | "inspect don't mock" convention not stated | GraphQL responses are real read models | `[ADD]` (convention, not feature) |
| SSE/live updates | Strong — Control Room, cycle board, egress counters, targeted payment-detail chip | none | ADR-N4, real operational freshness need | `[MVP]` |
| Network interception | Adequate by design — narrow legitimate uses exist | scope/policy not written down | see §5 — interception belongs at the simulated-external boundary, not core flows | `[ADD]` (policy) |
| Auth/session (storage state) | Strong — per-role `storageState`, BFF cookie | none | 11 real roles | `[MVP]` |
| Multi-role tests | Strong — every command has a role matrix | none | real operator segregation | `[MVP]` |
| Tenant isolation | Strong — RLS/GUC two-token tests already frozen (Keycloak-26 §13) | frontend-facing cross-reference (G-1, prior review) | multi-tenant is a real platform property | `[MVP]` |
| Accessibility | Strong — axe-core gate + per-screen ARIA rules | ARIA snapshot baselines | operator console must be usable under assistive tech | `[MVP]` |
| Visual regression | **Missing, by design choice** | none proposed | low business value vs. maintenance cost for a data-dense console | `[REJECT]` full-page; `[DEFER P2]` component-token snapshots |
| Trace debugging | Adequate — CI convention, not a UI feature | trace-on-first-retry policy not written | engineering practice | `[ADD]` (Iteration-0 CI policy) |
| Retries/flaky analysis | Adequate — anti-flakiness rules frozen (no `waitForTimeout` etc.) | see §6 | correctness of async domain flows | `[MVP]` |
| Fixtures/test data | Strong — simulation engine as a deterministic fixture factory | fixture taxonomy not written | see §7 | `[ADD]` (taxonomy) |
| Parallel execution | Adequate — per-worker isolation flagged (G-4, prior review) | isolation strategy not yet chosen | multi-tenant platform, real concurrency | `[MUST-FIX]` in Iteration 0 |
| Passkeys/WebAuthn | Correctly deferred | `security_admin`/passkey login is `[P1]` per Keycloak blueprint | real hardening path, not MVP-blocking | `[P1]` |
| localStorage/sessionStorage negative check | Strong — `page.localStorage()` (PW 1.61) makes this a first-class, clean assertion | none | ADR-N3's single most important negative test | `[MVP]` |

---

## 3. Current UI Testability Review

| Workspace | Current Test Value | Gaps | Recommended Improvement | Business Reason |
|---|---|---|---|---|
| Operations Control Room | SSE live counters, stale-state, role-filtered links — teaches auto-waiting on live data + reconnect/backoff | none material | — | daily triage must feel live and degrade visibly |
| Payments & Files | search/filter/table/pagination — good, but table+filter risks repeating the same pattern as every other list screen | upload not designed to depth | `[ADD]` drag-drop zone + progress + invalid-file validation (§4) | file rail is a real, distinct ingress channel with real failure modes |
| Payment Detail / Timeline | four status chips, tabs, drawer, SSE-targeted update, copy — genuinely distinct (deep object navigation + clipboard) | none material | — | this *is* the diagnostic heart of the console |
| Settlement & Liquidity | disabled/enabled command states, confirm modals, read-only ledger drill-down — distinct actionability teaching | none material | — | cycle-close eligibility is a real, non-trivial precondition |
| Egress & Delivery | command flows (retry/cancel), DLQ/manual-intervention queue, signature status — distinct command-flow teaching | receipts are "view," not "download" | `[ADD]` receipt download (§4) | receipts are audit artifacts, not just display data |
| Reconciliation & Cases | work queue, assign/resolve/escalate — distinct multi-role teaching, **but no race/conflict design** | no stale-assignment-conflict state | `[ADD]` assignment race/stale-conflict (§4) | a shared work queue **will** produce assignment races in real operator teams |
| Simulation Lab | launch/replay/seed/generated-events-with-deep-links — **the single richest fixture-creation surface in the system** | none material | — | this is the deterministic-fixture engine every other workspace's tests lean on |
| Reference Data / Admin | CRUD + diff preview + audit trail + validity windows — distinct forms/validation teaching, already has version-conflict design | none material | — | config drives settlement/routing/validation — real consequence |
| Evidence / Audit | progressive disclosure, role-gated raw, copy buttons, correlation/trace ids — distinct security-visibility teaching | none material | — | proof surface, deliberately not a data dump |

`[TESTABILITY VERDICT]` Eight of nine workspaces already teach something structurally different from "table + filter." The ninth (Payments & Files) *is* a table+filter screen at its core, correctly — its differentiator is meant to be file upload, which is under-designed. Fixing that (§4) is the single highest-leverage addition in this review.

---

## 4. Business-Driven Playwright Feature Backlog

Every row below satisfies all seven criteria (business sense, learning value, workspace home, testable state, role, test data, flakiness control).

| Feature | Workspace | Business Purpose | Playwright Learning Value | MVP/P1/P2 |
|---|---|---|---|---|
| Live SSE counters + stale-data indicator | Control Room | operators must trust freshness at a glance | auto-waiting, live-UI assertions, event-arrival (not timer) waits | `[MVP]` (existing) |
| Disconnected live-feed state + manual reconnect | Control Room | SSE will drop in a real network; must degrade visibly | network-condition simulation, reconnect-button flow | `[MVP]` (existing) |
| Role-filtered quick links | Control Room | 11 real roles, not display roles | role-based visibility assertions | `[MVP]` (existing) |
| **File drag-drop zone + upload progress** `[ADD]` | Payments & Files | pain.001 batch files are large; operators need drop-zone + progress, not a bare file input | `setInputFiles`, upload progress polling, large-payload handling | `[MVP]` |
| **Invalid-file validation (client + server)** `[ADD]` | Payments & Files | malformed/oversized files are a real ingress failure mode (main blueprint's own anomaly catalog) | negative-path testing, dialog/toast assertion, 422-equivalent handling | `[MVP]` |
| Idempotency-conflict toast | Payments & Files | duplicate submission is a real payments hazard | 409-path negative test, toast assertion, no state change | `[MVP]` (existing, now explicitly named as the canonical 409 teaching surface) |
| Advanced search (Payment ID/EndToEndId/UETR) + URL filters | Payments & Files | operators search by any of three real identifiers | `getByLabel`, URL/query assertions, deep-link round-trip | `[MVP]` (existing) |
| Four separate status chips + SSE-targeted update | Payment Detail | business/settlement/egress/reconciliation are genuinely independent axes | multi-element assertion, targeted (not global) live update | `[MVP]` (existing) |
| Timeline append (not full reload) | Payment Detail | operators watch a payment's story build | incremental DOM assertion, ordered-list semantics | `[MVP]` (existing) |
| Copy correlation/trace/hash buttons | Payment Detail, Evidence | support workflows need copyable ids | clipboard permission + assertion via `page.evaluate` read-back or `aria-live` confirmation | `[MVP]` (existing) |
| Role-gated egress actions on Payment Detail | Payment Detail | not every role may retry/cancel from the object view | role-based command visibility, cross-workspace consistency check | `[MVP]` (existing) |
| Cycle close / run netting with disabled-until-eligible button | Settlement & Liquidity | cycles have real state preconditions | actionability (`toBeEnabled`), state-setup-then-assert | `[MVP]` (existing) |
| Ledger impact as strictly read-only tab | Settlement & Liquidity | ledger must never be UI-editable (frozen invariant) | negative test: no write control exists in the DOM at all | `[MVP]` (existing) |
| Retry/cancel delivery, no optimistic UI | Egress & Delivery | real transport commands, real consequence | command-then-wait-for-backend-state pattern (anti-optimism) | `[MVP]` (existing) |
| **Delivery receipt download** `[ADD]` | Egress & Delivery | receipts are audit evidence operators export to reconcile externally | `page.waitForEvent('download')`, saved-file assertion | `[P1]` |
| Manual-intervention/DLQ queue | Egress & Delivery | dead-lettered artifacts are a real terminal state | queue-count SSE + row-level action gating | `[MVP]` (existing) |
| Work queue + assign/resolve/false-positive/escalate | Reconciliation & Cases | shared triage queue, real operator team behaviour | multi-user flow, command-then-state-refresh | `[MVP]`/`[P1]` (existing) |
| **Assignment race / stale-conflict state** `[ADD]` | Reconciliation & Cases | two operators can legitimately race to assign the same exception in a shared queue — this already happens in the Reference Data editor's version-conflict pattern; the exception queue needs the same discipline | two-browser-context race test, optimistic-locking assertion, "already assigned" toast instead of silent overwrite | `[P1]` |
| Launch/replay with deterministic seed | Simulation Lab | this **is** the platform's own fixture-creation mechanism | API+UI sync, traceability from seed to created objects, avoiding brittle DB setup entirely | `[MVP]` (existing) |
| Generated-events trace with deep links | Simulation Lab | operators (and tests) must follow a scenario to its effects | cross-workspace deep-link chains, deterministic replay equality assertion | `[MVP]` (existing) |
| Create/edit/deactivate with diff preview | Reference Data / Admin | config changes have real downstream consequence | form validation, combobox/select, diff-view assertion | `[MVP]` (existing) |
| Version-conflict on stale save | Reference Data / Admin | concurrent config edits are real | optimistic-locking negative test (the pattern §4's new reconciliation row reuses) | `[MVP]` (existing) |
| Raw/parsed progressive disclosure, role-gated | Evidence / Audit | raw payloads are sensitive; disclosure must be deliberate | `aria-expanded` disclosure pattern, role-based DOM-absence assertion (not just hidden) | `[MVP]` (existing) |
| Signature verdict + payload hash | Evidence / Audit | tamper-evidence is a real security property | text assertion on cryptographic-looking values, no raw-crypto-in-test-fixture needed (verdict is enough) | `[MVP]` (existing) |

---

## 5. Advanced Playwright 1.61 Learning Surfaces

| Advanced Topic | Business Feature | Why It Is Legitimate | Test Design Hint |
|---|---|---|---|
| WebAuthn/passkeys | `security_admin` passkey login | Keycloak blueprint already specifies passkeys as `[P1]` hardening — real, not invented for testing | PW 1.61 virtual authenticator registers/asserts a credential in CI without hardware; scope to the P1 passkey story, not MVP |
| `page.localStorage()`/`page.sessionStorage()` negative check | ADR-N3's core guarantee: no browser token | the single most important security property this frontend has | assert both APIs return no token-shaped value post-login; PW 1.61 makes this a direct API call, not an `evaluate()` hack |
| Network *inspection* (not mocking) for BFF/backend calls | verifying REST commands and GraphQL reads hit the right endpoint with the right shape | operators' trust in the console depends on correct wiring, not on decoration | `page.waitForResponse()` / `route.continue()` pass-through with logging — inspect, don't fulfill, for core flows |
| Trace/video retained on first retry only | CI cost control for a large 9-screen suite | engineering practice, not a UI feature | Playwright's `trace: 'retain-on-first-failure'` / `video: 'retain-on-failure'` config — Iteration-0 CI policy |
| Accessibility snapshots (`toMatchAriaSnapshot`) | operator console usable under assistive tech | axe-core catches violations; ARIA snapshots catch structural regressions axe won't | baseline one snapshot per workspace's primary state; review diffs like a visual-regression tool, but for structure not pixels |
| File drag-drop + upload for payment files | pain.001 batch files are a real ingress channel | see §4 | `setInputFiles` for the input path; drop-zone drag events for the DnD path |
| Download for delivery receipts / evidence bundles | audit export is a real operator need | see §4 | `page.waitForEvent('download')`, assert filename pattern + non-empty content |
| Route mocking **only** at the simulated-external-counterparty boundary | the CSM/scheme counterparty is already a simulated boundary in the domain design (failure profiles) | mocking a boundary the domain *itself* models as external is honest; mocking the BFF/backend is not — it would hide the very integration bugs this console exists to catch | reserve `route.fulfill`/`route.abort` for scenarios *outside* what the simulation engine already covers (e.g., a raw network timeout to the BFF, not a CSM business failure — that's what `failure_profiles` are for) |
| Multi-project (browser/device) coverage | operator console is desktop-first (frozen) | still worth Chromium+Firefox+WebKit coverage for a real product; no mobile project needed per the desktop-first freeze | 3 desktop browser projects; no device emulation project in MVP |
| Fixtures for users/roles/tenants/scenarios | 11 real roles, real tenant/branch model, real deterministic scenarios | this is the platform's actual identity and data model, not test scaffolding | `test.extend` fixtures: `asRole(role)` → storageState, `apiClient` → REST helper, `seededScenario(profile)` → simulation launch |
| Parallel tests with isolated tenant/branch/seed | multi-tenant platform; real concurrency property | see §6/§7 | per-worker tenant or seed-namespace, resolved as an Iteration-0 foundation task (carried from the prior compatibility review's G-4) |

`[REJECT]` WebAuthn is **not** added merely because Playwright 1.61 supports it — it rides the Keycloak blueprint's own P1 passkey decision, no earlier.

---

## 6. Anti-Flakiness Architecture Review

| Flaky Risk | Where It Appears | Business Cause | Required Design Control | Playwright Strategy |
|---|---|---|---|---|
| SSE timing | Control Room, cycle board, egress counters, payment-detail chip | live feeds are asynchronous by nature | ADR-N4 SSE reconnect/backoff already frozen | assert on event arrival / resulting DOM state, never `waitForTimeout`; `expect.soft.poll()` for eventually-consistent counters |
| Async settlement | Settlement & Liquidity | cycle close is a real async backend process | no optimistic UI (frozen); explicit `Closing` intermediate state | poll the status chip via `expect(locator).toHaveText(...)` (built-in retry), not a sleep |
| Delayed egress delivery | Egress & Delivery | real transport has retry/backoff | delivery status has explicit intermediate states | assert on `Delivery status` chip transitions, not on wall-clock delay |
| Table row reordering | any `EntityTable`/`WorkQueueTable` | live-updating queues can reorder rows under a test's cursor | overviews SSE-live, **detail/list tables query-driven** (frozen split) | assert on row content via testid, not row index; re-query after a known action |
| Toasts disappearing | every command | toasts are transient by UX design | fixed minimum-visible-duration (UX concern) | assert toast appearance immediately after the triggering action, not after a delay |
| Disabled buttons becoming enabled | Settlement (Close cycle), Reference Data (Save) | real precondition-based enablement | actionability is a genuine domain state, not decorative | `expect(locator).toBeEnabled()` — Playwright's built-in retry handles the transition |
| Background jobs | outbox dispatch, settlement cycle scheduler, reconciliation runner | real async backend workers | events/read-models are the test's synchronization point, not the job's internal timing | wait for the **read-model effect** (e.g., new row, new status), never for a job to "finish" |
| Upload processing | Payments & Files file upload | large files process asynchronously | explicit upload-progress + processed-count states | assert on the progress/processed-count read model, not a fixed wait |
| Parallel workers | full suite | 9 workspaces × many roles = large suite, must parallelize | per-worker tenant/seed isolation (§7) | Playwright's built-in worker parallelism + isolated fixtures |
| Shared test users | multi-role tests | 11 roles could tempt shared-login shortcuts | per-role `storageState`, never shared mutable users | one seeded user per role per worker (or per-worker tenant with all roles) |
| Tenant data leakage | any RLS-backed screen | real multi-tenant risk, not just test hygiene | RLS/GUC already enforces this server-side (Keycloak-26 §13) | two-token cross-tenant test is the canonical proof; UI tests inherit the guarantee, don't re-derive it |
| Clock/time dependencies | calendars, cut-offs, `as_of` snapshots | real business-time logic | `ClockPort` is already a frozen abstraction in the backend | tests seed a known `as_of`/business date via the simulation/API layer, never rely on wall-clock `now()` |

`[FLAKY-RISK]` rules reaffirmed, none new: no `waitForTimeout`; assert on business states/events; deterministic simulation seeds (ADR-N7); stable test-ids; accessible labels; explicit empty/error states; BFF/API helpers for setup; isolated tenant/branch per worker where needed; trace/video on failure.

---

## 7. Test Data and Fixture Design

| Fixture | Created By | Used For | Isolation Strategy |
|---|---|---|---|
| User/role identities (11 roles) | Testcontainers seed (Keycloak realm import) | every role-gated test | one seeded user per role, namespaced per worker |
| Tenant / branch | Testcontainers seed | tenant/branch isolation tests | one tenant+branch pair per worker (or shared tenant with worker-scoped sub-data — decided in Iteration 0) |
| Payment fixtures (incl. JSON_DIRECT) | **Simulation Lab** (API-triggered) | Payments & Files, Payment Detail tests | scenario seed is the isolation key — deterministic, replayable, no shared mutable rows |
| File fixtures (pain.001 batches) | **API setup** (direct upload via REST, bypassing UI when the UI isn't under test) or **UI** (when upload itself is under test) | Payments & Files upload tests | worker-scoped file naming; content is deterministic per fixture, not random |
| Settlement cycle fixtures | **Simulation Lab** replay to a known cycle state | Settlement & Liquidity tests | seed determines cycle id deterministically |
| Egress artifact fixtures | **Simulation Lab** (a settled payment's egress artifact is a downstream effect of the same seed) | Egress & Delivery tests | same seed-based determinism |
| Reconciliation exception fixtures | **Simulation Lab** failure profile (deliberately injects a mismatch) | Reconciliation & Cases tests, including the new assignment-race test | seed determines the exception; the race test itself uses **two parallel API/UI contexts against the same seeded exception** — the only fixture that's intentionally *shared* within one test |
| Simulation scenario fixtures | **Testcontainers seed** (the scenario catalog itself) | Simulation Lab tests | scenario catalog is read-only reference data, safe to share across workers |
| Audit/evidence fixtures | derived automatically from any command (audit is same-transaction) | Evidence/Audit tests | inherits the isolation of whatever fixture produced the command |
| Reference-data catalog fixtures | **Testcontainers seed** (baseline) + **UI** (when the CRUD flow itself is under test) | Reference Data / Admin tests | baseline shared read-only; mutations under test use a worker-scoped catalog row |

`[FIXTURE]` **Principle:** prefer the **Simulation Lab / API layer** over raw Testcontainers SQL seeding wherever the object graph has business logic behind it (payments, settlement, egress, reconciliation) — this exercises real code paths and keeps fixtures replayable, exactly the "avoid brittle database setup" value already named in the UI spec. Use **Testcontainers seed** only for structural reference data (roles, tenants, catalogs) that has no business-logic path to create it. Use **UI-driven creation** only when the creation flow itself is the thing under test.

---

## 8. Automation Architecture Recommendation

| Pattern | Adopt / Reject / Defer | Why |
|---|---|---|
| Classic monolithic Page Object (one class, all locators+methods per page) | `[REJECT]` | object-detail pages share components (tabs, drawer, status chips) across all 9 screens; a monolithic POM per page duplicates that logic 9 times |
| **Component Objects** (`StatusChipGroup`, `EntityTable`, `EvidenceDrawer`, `CommandButton`) | `[ADOPT]` | mirrors the frontend's own component foundation (§11a-b of the blueprint) — one component object per shared UI component, reused across every screen that has one |
| **Screen Objects** (thin, per-workspace composition of component objects) | `[ADOPT]` | one per workspace/object-detail page; composes component objects, adds only screen-specific locators |
| **API Fixtures** (`test.extend` for auth/session, REST command helpers, simulation-scenario launch) | `[ADOPT]` | the BFF/REST/simulation layer is the natural, fast, business-real setup path (§7) |
| **Test data builders** | `[DEFER P1]` | useful once fixture variety grows past what direct simulation-scenario parameters cover; not needed for the MVP screen set |
| **Assertions as domain matchers** (e.g. `expectFourStatusChips`, `expectAuditEntryFor(command)`) | `[ADOPT]` | the four-status-separation and same-transaction-audit rules are repeated business invariants worth naming once, not re-asserted ad hoc per test |
| **Screenplay pattern** (actor/ability/task/interaction) | `[REJECT]` | too much ceremony for this team size and screen count; the project's own anti-overengineering discipline (already applied to FleetSignal/Charter Loop) argues against it here too |

`[ADOPT]` **Recommended model, exactly as defaulted:**
```text
Screen Object + Component Object + API Fixture + Domain Assertion Helpers
```
No heavy framework. Component objects absorb the shared shadcn/TanStack kit; screen objects stay thin; API fixtures do setup through the BFF/simulation layer, never raw SQL where a business path exists; domain assertion helpers encode the platform's own invariants (status separation, audit-per-command, empty≠unauthorized) once.

---

## 9. Business Feature Corrections to UI Specs

| Document | Required Change | Reason | Priority |
|---|---|---|---|
| `sepa-nexus-first-3-screens-ui-spec.md` | Payments & Files §5.4/§5.5/§5.7: add drag-drop zone, upload-progress state, and client+server invalid-file validation to the `Upload file` flow (currently a bare dialog) | §4 — file rail is under-designed relative to its real failure modes | `[MVP]` |
| `sepa-nexus-next-3-screens-ui-spec.md` | Reconciliation & Cases §7.4/§7.5/§7.7: add an assignment-race/stale-conflict state to `Assign to me` — a second operator attempting to assign an already-claimed exception gets an "already assigned to X" toast, not a silent overwrite | §4 — real hazard in a shared work queue, currently undesigned | `[P1]` |
| `sepa-nexus-next-3-screens-ui-spec.md` | Egress & Delivery §6.5/§6.6: change `View delivery receipt` to include a `Download receipt` action with a defined file/content-type | §4/§5 — receipts are audit exports, not just display | `[P1]` |
| `sepa-nexus-react-nextjs-frontend-blueprint.md` | §19 (Playwright strategy): add the "inspect, don't mock" convention for GraphQL/BFF calls, and the route-mocking-only-at-the-simulated-boundary policy | §5 — prevents future specs from reaching for mocks where the simulation engine already provides real fixtures | `[SHOULD-FIX]` |
| `sepa-nexus-react-component-foundation-blueprint.md` | §9/§10 (Adoption Plan / Rejected Options): add the Screen Object + Component Object + API Fixture + Domain Assertion Helpers model (§8 here) as the named automation architecture | closes the "what pattern do we actually use" gap the adoption plan left open | `[SHOULD-FIX]` |

None of these blocks Iteration 0; the `[MVP]` file-upload item is the one worth sequencing into the Iteration-1 payment-spine work (file rail is already `[MVP]` there) rather than deferred.

---

## 10. Rejected Test-Learning Ideas

| Rejected Idea | Why Rejected | Better Business-Aligned Alternative |
|---|---|---|
| Fake shopping cart / drag-drop reordering unrelated to payments | zero business meaning in a payments operator console | file drag-drop upload (§4) — same DnD learning value, real business object |
| Random/decorative animations for visual-regression practice | maintenance cost with no operator value; the console is data-dense, not decorative | ARIA-snapshot structural regression testing (§5) — catches real regressions, not pixel noise |
| Artificial iframe just to test frame-handling | no SEPA Nexus screen has a legitimate iframe | none needed — frame-handling isn't part of this console's real surface; skip it rather than invent one |
| GraphQL mutations added just to test mutation flows | violates the frozen read-only GraphQL boundary; would teach a pattern the real system forbids | REST-command mutation testing (already the correct, real pattern) |
| Browser token storage added just to exercise localStorage assertions | destroys the exact security property ADR-N3 exists to guarantee | the **negative** test (`page.localStorage()` returns no token) *is* the valuable assertion — the absence is the feature |
| Arbitrary popups / modal spam | no business trigger; teaches nothing about real dialog-handling context | the existing confirm-dialog set (Close cycle, Cancel delivery, Deactivate participant) already covers real modal patterns |
| Gamified UI unrelated to payments (badges, streaks, etc.) | operator consoles are not games; would undermine the console's own credibility as a portfolio piece | none — the domain itself (settlement, reconciliation, cases) already has enough real state machines to teach state-transition testing |
| Fake artificial delays "for flaky-test practice" | manufactured flakiness teaches bad instincts (waiting on time instead of state) | the real async surfaces already present (SSE, settlement, egress, upload processing — §6) are sufficient and teach the *correct* instinct |
| Direct DB reset button in UI | breaks the BFF/REST/GraphQL boundary and the audit trail; a UI control that bypasses the domain entirely | Simulation Lab launch/replay **is** the sanctioned reset-to-known-state mechanism, and it's real |

---

## 11. MVP / P1 / P2 Learning Roadmap

| Item | Scope | Playwright Surface Unlocked |
|---|---|---|
| File upload richness (drag-drop, progress, invalid-file) | `[MVP]` (Iteration 1, file rail) | upload interactions, progress polling, negative validation |
| Idempotency-conflict toast (existing, now explicitly named) | `[MVP]` (Iteration 1) | canonical 409 negative-test teaching surface |
| Four-status chips + SSE-targeted update (existing) | `[MVP]` (Iteration 1–2) | multi-axis assertion, targeted live update |
| Settlement disabled/enabled + confirm modals (existing) | `[MVP]` (Iteration 4) | actionability, precondition-based state |
| Simulation Lab launch/replay (existing) | `[MVP]` (Iteration 3) | deterministic fixture creation — the backbone of every other test's setup |
| Reference Data version-conflict (existing) | `[MVP]` | optimistic-locking pattern, reused by the reconciliation addition below |
| localStorage/sessionStorage negative test (existing, PW 1.61-enhanced) | `[MVP]` (Iteration 0/1) | ADR-N3's core security assertion |
| axe-core + ARIA snapshots (existing + new baseline) | `[MVP]` (Iteration 0 gate) | accessibility regression protection |
| Reconciliation assignment-race/stale-conflict | `[P1]` | two-context concurrency testing, optimistic locking under contention |
| Delivery receipt download | `[P1]` | `waitForEvent('download')` pattern |
| Evidence bundle download | `[P1]` (already scoped) | same download pattern, security-gated |
| Passkeys/WebAuthn for `security_admin` | `[P1]` (Keycloak blueprint) | virtual authenticator, credential lifecycle |
| Component-level visual/token snapshot testing | `[P2]` | narrow, low-maintenance visual protection (not full-page) |
| Multi-project browser/device matrix | `[P2]` | cross-browser confidence beyond the MVP single-project baseline |

---

## 12. Final Decision

```text
DECISION: PARTIALLY READY
WHY: The frontend is already unusually rich for Playwright learning — BFF/session, GraphQL-read/REST-command split, SSE with reconnect, 11 real roles, four-axis status separation, drawers/tabs/dialogs/toasts, URL-state, a four-state component family, an axe-core gate, and a deterministic simulation engine that manufactures real fixtures through real business paths. Eight of nine workspaces already teach something structurally distinct. Two concrete, business-real gaps keep it from full readiness: file upload is under-designed relative to a real ingress channel's failure modes, and the Reconciliation work queue has no designed behaviour for a genuine multi-operator assignment race. Both are ADD, not invented — the domain already implies them.
MUST_ADD:
1. File drag-drop + upload progress + invalid-file validation (Payments & Files, MVP)
2. Reconciliation exception assignment-race / stale-conflict state (Reconciliation & Cases, P1)
3. Delivery receipt download, not just view (Egress & Delivery, P1)
4. Screen Object + Component Object + API Fixture + Domain Assertion Helpers as the named automation architecture (Iteration-0 test foundation)
5. Per-worker tenant/seed isolation strategy for parallel execution (Iteration-0 test foundation, carried from the prior compatibility review's G-4)
MUST_REJECT:
1. Fake shopping cart / drag-drop unrelated to payments
2. Route-mocking core BFF/backend flows (undermines the domain realism the console exists to demonstrate)
3. GraphQL mutations added just to exercise mutation testing
4. Browser token storage added just to exercise localStorage assertions
5. Direct DB reset button in UI, arbitrary popups, gamified UI
NEXT: update frontend UI specs and Iteration 0/1 backlog with the approved Playwright learning surfaces
```

---

*End of Playwright test-learning business development review. `[NO-CODE]` — no tests, no React code, no ground-up redesign. Verified against Playwright 1.61 (June 2026: `page.localStorage`/`sessionStorage`, WebAuthn virtual authenticator, `expect.soft.poll()`, WebSocket-in-trace) and mature network-interception practice (narrow scope, register-before-navigate, inspect-don't-mock for core flows). Consistent with ADR-N1…N8 and every frozen frontend/security decision. Two additions, one clarification, zero artificial features — the operator console teaches Playwright because it is a real console, not because it was built to.*
