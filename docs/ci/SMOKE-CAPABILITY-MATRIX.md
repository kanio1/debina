# ADR-N16 smoke capability matrix

Inventory recorded at `676d09e` on `rebase/enterprise-evolution`. This is an implementation inventory, not proof that a browser journey has run.

| Journey | Current evidence | Stable observable / locator | Status |
|---|---|---|---|
| Keycloak login | `frontend/src/app/api/auth/login/route.ts`, callback and realm `submitter`/`approver` users | `/api/session` returns BFF session claims; browser receives only HttpOnly session cookie | IMPLEMENTABLE after Dagger runtime gate |
| BFF session | `frontend/src/app/api/session/route.ts` | authenticated `200` JSON `claims`, no token response field | IMPLEMENTABLE after Dagger runtime gate |
| Health/runtime availability | Spring Boot Actuator dependency and local deployment architecture | endpoint and runtime readiness must be confirmed from actual configured path | INVENTORY-NEEDS-RUNTIME-PROBE |
| JSON_DIRECT submission | `frontend/src/app/payments/page.tsx` and BFF `POST /api/payments`; UC-SCT-001 | accessible form labels plus `payments.submit.*` test IDs; success text `Payment submitted.` | IMPLEMENTABLE after Dagger runtime gate |
| Maker-checker approval | `ApprovalQueue`, BFF approval route, UC-SCT-APPROVAL-001 | `payments.approvals.queue`, approval confirm button and success text `Approval recorded.` | IMPLEMENTABLE after Dagger runtime gate |
| Payment Detail + evidence/lineage | `/payments/[id]`, BFF detail/timeline, UC-AUDIT-001 | `payment.detail.end-to-end-id`, `payment.detail.iso-identifiers.row`, timeline event reference | IMPLEMENTABLE after Dagger runtime gate |

ADR-N16 supersedes the older broad Playwright deferral only for this capped smoke. No Control Room, broad acceptance, visual regression or cross-browser work is included.

## D3 audit and governance resolution (2026-07-22)

The initial audit found all six journeys capability-blocked on an absent Playwright/Dagger foundation and EPIC-24 sequencing. The approved Phase D ADR-N16 exception now authorizes only the minimum local Chromium foundation, direct ephemeral Dagger graph and six named journeys. EPIC-24's general acceptance sequencing, all story status, Control Room/reporting/SSE scope, axe-core, visual/cross-browser coverage, remote CI and runtime compose remain unchanged. D3A implements only login, BFF session and health; it must not run the other three business journeys.
