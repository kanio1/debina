# HANDOFF

## Zadanie

Wave 9 dostarcza read-only GraphQL approval spine i maker-checker workspace w Payments & Files przez Next.js BFF. Nie pushować.

## Zrobione

- Commity `bdb38ae`..`20acee5` dostarczają secured Query-only GraphQL, source-owned payment port, query limits, prod introspection, PostgreSQL 18 RLS/cursor/detail proof, deterministic GraphQL codegen oraz BFF GraphQL/approve/reject routes.
- `298d1c4` dodaje role-gated approval queue do Payments & Files: wyraźne loading/empty/error/unauthorized, cursor load more, self-approval block, confirm/reject comment i refetch po server-confirmed command bez optimistic update.
- Fresh PostgreSQL 18 wykonał V1–V60; real Keycloak PKCE utworzył BFF HttpOnly session, a approver BFF GraphQL queue zwrócił `200` i pustą connection. Naprawiono runtime Flyway classpath oraz BFF scope `openid sepa-guc`.

## Utknęliśmy na

Nie ma Class C blokera. Nie udowodniono jeszcze pełnego maker→approver approve/reject na realnym runtime: świeża baza nie ma source-seeded approval matrix, a live `sepa-guc` session ma `tenantId: null` (mimo roli i branch), więc trzeba zdiagnozować source-owned tenant claim. Brak też dwóch pełnych backend regressions i validatorów. Lint jest GREEN z jednym istniejącym warningiem TanStack `useReactTable`.

## Plan na następny krok

Uruchom focused + full test/validator gate; następnie dokończ real runtime przez source-backed approval-matrix seed i tenant-claim diagnosis, wykonaj maker submit oraz approver approve/reject przez BFF i zapisz evidence. Sprzątnij własny tymczasowy kontener `debina-wave9-postgres` i procesy dev po proof.

## Pułapki, których nie wolno powtórzyć

- Maven przepisuje `build/generated-spring-modulith/javadoc.json`; odtwórz go przez `apply_patch`.
- Cursor decoder musi używać Java regex `"\\|"`, nie nadmiernie escaped `"\\\\|"`.
- BFF GraphQL nie przyjmuje browser query text ani backend URL; tylko allowlisted operation names i variables.
- Adapter GraphQL używa tylko `com.sepanexus.modules.ApprovalQueueQuery`, nie package `.service`.
- Persistent compose PostgreSQL jest tylko na V20; nie migruj go bez świadomej decyzji. Użyty świeży, własny kontener to `debina-wave9-postgres` na porcie 15432.
