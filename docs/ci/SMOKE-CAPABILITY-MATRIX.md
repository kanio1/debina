# ADR-N16 smoke capability matrix

Inventory recorded at `676d09e` on `rebase/enterprise-evolution`, with runtime
proof completed on 2026-07-23. This matrix proves only the capped local
ADR-N16 Chromium smoke, not broader product acceptance.

| Journey | Current evidence | Stable observable / locator | Status |
|---|---|---|---|
| Keycloak login | `frontend/src/app/api/auth/login/route.ts`, callback and realm `submitter`/`approver` users | `/api/session` returns BFF session claims; browser receives only HttpOnly session cookie | RUNTIME-PROVEN |
| BFF session | `frontend/src/app/api/session/route.ts` | authenticated `200` JSON `claims`, no token response field | RUNTIME-PROVEN |
| Health/runtime availability | Spring Boot Actuator and the Dagger PostgreSQL/Kafka/Keycloak/backend/frontend graph | bounded service readiness and authenticated application shell | RUNTIME-PROVEN |
| JSON_DIRECT submission | `frontend/src/app/payments/page.tsx` and BFF `POST /api/payments`; UC-SCT-001 | accessible form labels, success text and correlated database/ISO/outbox/Kafka evidence | RUNTIME-PROVEN |
| Maker-checker approval | `ApprovalQueue`, BFF approval route, UC-SCT-APPROVAL-001 | two BrowserContexts, self-denial, approval, audit/history and Kafka evidence | RUNTIME-PROVEN |
| Payment Detail + evidence/lineage | `/payments/[id]`, BFF detail/timeline, UC-AUDIT-001 | detail/timeline/ISO drawer plus database and Kafka correlation | RUNTIME-PROVEN |

ADR-N16 supersedes the older broad Playwright deferral only for this capped smoke. No Control Room, broad acceptance, visual regression or cross-browser work is included.

## D3 audit and governance resolution (2026-07-22)

The initial audit found all six journeys capability-blocked on an absent
Playwright/Dagger foundation and EPIC-24 sequencing. The approved Phase D
ADR-N16 exception authorized only the minimum local Chromium foundation, direct
ephemeral Dagger graph and six named journeys. They are now classified together
under `dagger call smoke-suite`, which executes `smoke-auth` followed by the
three isolated `smoke-payments` leaves sequentially. The old `smoke` name is a
deprecated alias of `smoke-suite`.

EPIC-24's general acceptance sequencing, all story status, Control
Room/reporting/SSE scope, axe-core, visual/cross-browser coverage, remote CI and
runtime compose remain unchanged.
