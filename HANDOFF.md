# HANDOFF

## Zadanie

Wave 8: source-owned application command audit, single-payment approve/reject and replay-safe
approval expiry. Final implementation and test gates are complete; do not push.

## Zrobione

- Baseline `edf6dcb`; current committed implementation HEAD `e014a89` before this evidence update.
- Commits `73501af` through `7396725` implement audit persistence/ports, decision commands,
  expiry service role/function, object authorization and proof slices.
- `ApprovalSubmissionIntegrationTest` is 13/0/0 against PostgreSQL 18: approve/reject/idempotency,
  denied audit and fail-closed denial-audit failure, audit append rollback, two-checker race and
  approve/reject-vs-expiry races.
- `ApprovalDecisionKeycloakRuntimeTest` is 1/0/0 against real Keycloak 26.6.4. It validates a
  signed issuer token and approves another maker's payment. `infra/keycloak/realm-export.json`
  now gives `sepa-guc` the stable public subject mapper; the test first exposed that `sub` was absent.
- Mutation proof is recorded: suppressing approve audit caused one focused failure, then restore
  passed. Physical `txid_current`/`xmin`, approve/reject/expiry audit-failure, HTTP and final
  two consecutive backend regressions are green (514/0/0 each).

## Utknęliśmy na

No known Class C blocker or remaining Wave 8 implementation/proof gap. Perform a completion audit
against the user contract and current artifacts before declaring SUCCESS.

## Plan na następny krok

1. Inspect the current state against the full Wave 8 completion contract and declare SUCCESS only if
   every claimed surface is directly evidenced. Otherwise add the exact missing proof.

## Pułapki, których nie wolno powtórzyć

- Never edit applied V55–V60 migrations; use forward migrations.
- Maven rewrites `build/generated-spring-modulith/javadoc.json`; restore it with `apply_patch`.
- Keep `PENDING_APPROVAL` pre-FSM and no outbox on reject/expiry.
- Do not treat app logs, outbox, payment events or Keycloak events as application audit.
- Keycloak probe client is idempotent; `sepa-guc` now must retain the `oidc-sub-mapper` because
  approval identity is the stable token `sub`.
