# HANDOFF

## Zadanie

Wave 8 implementuje source-owned application command audit oraz single-payment approve/reject i
approval expiry dla syntetycznej platformy SEPA Nexus. Celem jest atomowy audit decyzji bez
naruszania one-writer-per-schema i bez mieszania approval axis z business FSM.

## Zrobione

- Commit `73501af` (`feat(audit): add immutable command audit boundary`) dostarczył EPIC-77 Story
  77.1: moduł `evidenceaudit`, V55-V57 z `audit.audit_log`, RLS, append-only grants, narrow
  auditor read role oraz ADR-W8-01. `CommandAuditMigrationTest` (fresh + V54 upgrade) i
  `CommandAuditArchitectureTest`/`ModularityTest` przeszły; log jest w
  `/tmp/DEBINA-COMMAND-AUDIT-AND-APPROVAL-DECISIONS-WAVE-8/audit-slice-focused-green.log`.
- EPIC-77 jest source-derived ownerem, a 76.3 zależy od właściwej capability
  `cap.audit.same-transaction-command-append`, nie od fikcyjnego substitute.
- W worktree (niecommitowane) rozpoczęto Story 77.2/76.3: `ApprovalDecisionService`, REST approve/
  reject i podstawowa PostgreSQL proof. `ApprovalSubmissionIntegrationTest` 5/0/0 dowodzi: approve
  → dokładnie jeden istniejący `payment.received` + audit; reject → audit bez outbox; replay bez
  duplikacji. Log `approval-decision-first-proof-green-4.log` w tym samym katalogu `/tmp`.

## Utknęliśmy na

Nie ma source/decision blocker. Niecommitowany approve/reject slice NIE jest kompletny: brakuje
custom `AuthorizationManager<MethodInvocation>` wykonanego przed service body, audited denied
attempt path, audit-failure rollback, two-checker and expiry races, expiry worker/RLS role,
Keycloak proof, mutation proof i pełne regresje. Nie commitować tej części przed tymi proofami.

## Plan na następny krok

Otwórz `ApprovalDecisionService` oraz `SecurityConfig`, zastąp role-only guard source-required
object-level `AuthorizationManager<MethodInvocation>`, następnie napisz i uruchom proof dla
same-tenant/branch approver, foreign tenant/branch, submitter/viewer i maker=self przed service
body wraz z denial-audit requirement.

## Pułapki, których nie wolno powtórzyć

- V55/V56 są już historycznie zachowane; błąd `pg_catalog.nullif` został naprawiony wyłącznie
  forward migration V57, nigdy nie edytuj zastosowanej migracji.
- `PaymentApprovalEntity` helper w starym teście zwraca approval ID, nie payment ID.
- PostgreSQL JDBC nie inferuje `Instant` w funkcji SQL; `JdbcCommandAuditPort` musi przekazywać
  `Timestamp`.
- Maven nadpisuje `build/generated-spring-modulith/javadoc.json`; przy końcowym cleanup przywróć
  go, jeśli nie jest świadomym artefaktem.
