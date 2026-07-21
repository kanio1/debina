# HANDOFF

## Zadanie
Wave 10 dostarcza read-only Audit Explorer i Evidence Drawer, od immutable `audit.audit_log` przez evidence-audit query port, GraphQL/BFF, do Payment Detail i workspace auditora.

## Zrobione
- Commity `4eba670`, `543a5d5`, `50f0805`, `70da598`, `5cd289f`: publiczny `AuditQueryPort`, PostgreSQL 18 Testcontainers proof, secured GraphQL fields, fixed BFF operations, deterministic codegen, Payment Detail Evidence drawer, URL-driven/cursor auditor workspace oraz Wave 9 planning reconciliation.
- `CommandAuditQueryIntegrationTest` PASS (3), GraphQL structural/runtime focused tests PASS (14), frontend codegen/typecheck/lint/build PASS (lint has existing TanStack warning).

## Utknęliśmy na
Nie wykonano jeszcze realnego Keycloak+BFF+backend smoke dla nowych audit operations, pełnych backend regressions ani wszystkich required validators. Drawer still lacks copy aria-live feedback; Story 24.8 split is still outstanding. Message evidence and export remain source/decision blocked.

## Plan na następny krok
Zbuduj/uruchom realny Keycloak+BFF+backend smoke dla audit operations, następnie wykonaj wymagane pełne regressions i validators.

## Pułapki, których nie wolno powtórzyć
- GraphQL codegen `typescript` + `typescript-operations` dubluje input type dla operation variable; obecny `codegen.yml` celowo używa tylko `typescript-operations`.
- Maven modyfikuje `build/generated-spring-modulith/javadoc.json`; nie commituj tej zmiany.
- Cursor decoder musi używać Java regex `"\\\\|"`.
