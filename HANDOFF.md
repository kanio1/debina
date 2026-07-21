# HANDOFF

## Zadanie

Wave 9 dostarcza read-only GraphQL approval spine i maker-checker workspace w Payments & Files przez Next.js BFF. Nie pushować.

## Zrobione

- Commity `bdb38ae`..`2e10738` tworzą secured Query-only GraphQL, source-owned payment port, query limits, prod introspection restriction oraz PostgreSQL 18 RLS/cursor/detail proof.
- `bd5d3aa` tworzy deterministic GraphQL TypeScript codegen (`pnpm run codegen:graphql`), drugi run ma ten sam SHA, typecheck przechodzi.
- `259b618` BFF proxy ma dwa allowlisted read operations; `92c2880` BFF ma CSRF/session/idempotency-protected approve/reject routes.

## Utknęliśmy na

Nie ma Class C blokera. Brakuje UI approval queue/detail i server-confirmed command refresh, BFF route tests/runtime proof, real Keycloak+BFF+backend journey, full regressions/validators. Lint jest GREEN z jednym istniejącym warningiem TanStack `useReactTable`.

## Plan na następny krok

Zbuduj client component approval queue w `frontend/src/components/payments/` i włącz go do `/payments`: fetch przez `/api/graphql`, approve/reject przez nowe BFF routes, bez optimistic update, z distinct loading/empty/error/unauthorized states.

## Pułapki, których nie wolno powtórzyć

- Maven przepisuje `build/generated-spring-modulith/javadoc.json`; odtwórz go przez `apply_patch`.
- Cursor decoder musi używać Java regex `"\\|"`, nie nadmiernie escaped `"\\\\|"`.
- BFF GraphQL nie przyjmuje browser query text ani backend URL; tylko allowlisted operation names i variables.
- Adapter GraphQL używa tylko `com.sepanexus.modules.ApprovalQueueQuery`, nie package `.service`.
