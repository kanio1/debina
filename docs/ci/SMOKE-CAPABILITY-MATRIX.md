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

## D3 audit (2026-07-22)

All six journeys are `CAPABILITY-BLOCKED`, not failed product behavior. The repository has no approved Playwright package/configuration or Dagger smoke runtime graph, and the current EPIC-24 sequencing record blocks Playwright until the missing Control Room exists. ADR-N16 permits only this capped scope; it does not by itself authorize a new frontend/runtime testing foundation. The implementation inventory above remains historical evidence: login/session, JSON_DIRECT submission, maker-checker approval and payment detail/lineage have routes and stable observables; health still needs a runtime probe. No browser test, product code or runtime-compose graph was created.
