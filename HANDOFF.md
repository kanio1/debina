# HANDOFF

## Zadanie

SEPA Nexus jest deterministyczną platformą treningową płatności. Wave 9 dostarczył read-only GraphQL approval spine oraz maker–checker workspace w Payments & Files przez Next.js BFF; nie pushowano zmian.

## Zrobione

- `planning/epics/EPIC-78-read-only-graphql-delivery.md` oraz ADR-W9 tworzą source-derived owner dla Wave 9. Commity `bdb38ae`..`c6a19ea` dostarczają secured Query-only GraphQL, payment-owned public read port, mechaniczne zero-Mutation/ownership proof, limity depth/complexity, production introspection restriction, PostgreSQL 18 RLS/cursor/detail proof, deterministic GraphQL codegen oraz BFF GraphQL/approve/reject routes.
- `298d1c4` dodaje do Payments & Files role-gated approval queue: osobne loading/empty/error/unauthorized, cursor load more, self-approval block, approve confirmation, obligatoryjny reject comment i refetch po server-confirmed command bez optimistic update.
- Realny smoke na Keycloak 26.6.4, Next.js BFF, Spring backend i świeżym PostgreSQL 18 przeszedł dla approve i reject: maker utworzył PENDING_APPROVAL, approver odczytał go przez BFF GraphQL, wysłał REST BFF command i po refetch kolejka była pusta. `FullRoleModelRealmTest` potwierdza `sub`; BFF normalizuje source-owned Organization tenant claim. Dwa pełne backend regressions: 526/0/0/0 każdy; frontend lint/typecheck/build, codegen drift i governance validators są GREEN. Szczegóły w `planning/programs/DEBINA-READ-ONLY-GRAPHQL-APPROVAL-WORKSPACE-WAVE-9.md`.

## Utknęliśmy na


Nic nie blokuje Wave 9. Playwright pozostaje prawidłowo zablokowany przez zamrożoną sekwencję pierwszych ekranów (Ops Control Room), więc EPIC-76 Story 76.7 i EPIC-24 Story 24.2B nie są ukończone. Batch approval, step-up i Evidence Drawer pozostają poza zakresem.

## Plan na następny krok


Otwórz `planning/README.md`, wykonaj zwykły capability-first wybór następnej analitycznie READY story i ponownie sprawdź jej bezpośrednie zależności, ownership oraz `verify:` przed rozpoczęciem.

## Pułapki, których nie wolno powtórzyć

- Maven przepisuje `build/generated-spring-modulith/javadoc.json`; odtwórz go przez `apply_patch`.
- Cursor decoder musi używać Java regex `"\\|"`, nie nadmiernie escaped `"\\\\|"`.
- BFF GraphQL nie przyjmuje browser query text ani backend URL; tylko allowlisted operation names i variables.
- Adapter GraphQL używa tylko `com.sepanexus.modules.ApprovalQueueQuery`, nie package `.service`.
- Persistent compose PostgreSQL jest tylko na V20; nie migruj go bez świadomej decyzji. Wave 9 użył własnego świeżego kontenera `debina-wave9-postgres` na porcie 15432, który trzeba po smoke usunąć.
- Realm import istniejącego Keycloak nie aktualizuje automatycznie zmian default client scopes; dla czystego runtime realm source musi zawierać `basic` scope z mapperem `Subject (sub)` oraz `sepa-guc`.
