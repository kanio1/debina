# HANDOFF

## Zadanie
Wave 10 dostarcza read-only Audit Explorer i Evidence Drawer, od immutable `audit.audit_log` przez evidence-audit query port, GraphQL/BFF, do Payment Detail i workspace auditora.

## Zrobione
- Commity `4eba670`, `543a5d5`, `50f0805`: publiczny `AuditQueryPort`, PostgreSQL 18 Testcontainers proof, secured GraphQL fields, fixed BFF operations, deterministic codegen, Payment Detail Evidence drawer oraz `/evidence` auditor workspace.
- `CommandAuditQueryIntegrationTest` PASS (3), GraphQL structural/runtime focused tests PASS (14), frontend codegen/typecheck/lint/build PASS (lint has existing TanStack warning).

## Utknęliśmy na
Nie wykonano jeszcze realnego Keycloak+BFF+backend smoke dla nowych audit operations, pełnych backend regressions, planning reconciliation/splits EPIC-24/78 ani wymaganych validators. Drawer/workspace są minimalne: workspace has no URL-driven filters/cursor load-more; drawer has no copy aria-live feedback yet. Message evidence and export remain source/decision blocked.

## Plan na następny krok
Uruchom focused backend GraphQL test suite po aktualnym SDL, następnie uzupełnij BFF/runtime testy i rozbuduj workspace o wymagane filters oraz cursor pagination.

## Pułapki, których nie wolno powtórzyć
- GraphQL codegen `typescript` + `typescript-operations` dubluje input type dla operation variable; obecny `codegen.yml` celowo używa tylko `typescript-operations`.
- Maven modyfikuje `build/generated-spring-modulith/javadoc.json`; nie commituj tej zmiany.
- Cursor decoder musi używać Java regex `"\\\\|"`.
