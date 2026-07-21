# HANDOFF

## Zadanie

Wave 8: source-owned application command audit, single-payment approve/reject and replay-safe
approval expiry. Do not push. Continue from the actual local HEAD, not the original baseline hints.

## Zrobione

- Baseline `edf6dcb`; current committed HEAD `7396725` (no push, clean worktree at checkpoint).
- Commits `73501af` through `7396725` implement audit persistence/ports, decision commands,
  expiry service role/function, object authorization and proof slices.
- `ApprovalSubmissionIntegrationTest` is 13/0/0 against PostgreSQL 18: approve/reject/idempotency,
  denied audit and fail-closed denial-audit failure, audit append rollback, two-checker race and
  approve/reject-vs-expiry races.
- `ApprovalDecisionKeycloakRuntimeTest` is 1/0/0 against real Keycloak 26.6.4. It validates a
  signed issuer token and approves another maker's payment. `infra/keycloak/realm-export.json`
  now gives `sepa-guc` the stable public subject mapper; the test first exposed that `sub` was absent.
- Mutation proof is recorded: suppressing approve audit caused one focused failure, then restore
  passed. Full backend regressions #2 and #3 were 505/0/0 before proof-only commit `7396725`.

## Utknęliśmy na

No Class C blocker. Wave is not yet SUCCESS: final evidence still needs a dedicated physical
same-transaction identity test, controlled audit-append failure for reject and expiry, HTTP
controller boundary evidence, final planning/skill validators, and two new consecutive full backend
regressions after `7396725`.

## Plan na następny krok

1. Add `CommandAuditTransactionIntegrationTest`: compare audit-row `xmin` with `txid_current()`
   inside one transaction and prove rollback.
2. Extend `ApprovalSubmissionIntegrationTest` with reject audit-port failure and an expiry failure
   by temporarily revoking the expiry function owner's execute privilege on `audit.append_command_audit`,
   restoring it in `finally`.
3. Add/extend MockMvc endpoint test for Idempotency-Key, RFC-7807 conflict/forbidden boundaries.
4. Update program/epic evidence, rerun focused suites, validators, and two full `./mvnw -f backend test`
   runs; restore `build/generated-spring-modulith/javadoc.json` after Maven; commit each slice.

## Pułapki, których nie wolno powtórzyć

- Never edit applied V55–V60 migrations; use forward migrations.
- Maven rewrites `build/generated-spring-modulith/javadoc.json`; restore it with `apply_patch`.
- Keep `PENDING_APPROVAL` pre-FSM and no outbox on reject/expiry.
- Do not treat app logs, outbox, payment events or Keycloak events as application audit.
- Keycloak probe client is idempotent; `sepa-guc` now must retain the `oidc-sub-mapper` because
  approval identity is the stable token `sub`.
