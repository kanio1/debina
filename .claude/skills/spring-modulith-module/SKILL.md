---
name: spring-modulith-module
description: Use when creating or modifying a Spring Modulith application module (a package under backend/src/main/java/.../modules/*) — controller/service/repository layering, module boundaries, and the DDD aggregate rule for this project.
---
# Spring Modulith module conventions

1. One top-level package per module (e.g. `modules.paymentlifecycle`). No other module may import an internal (`.internal`) subpackage.
2. Layering inside a module: `web` (Controller, DTO only, zero business logic) → `service` (Service, owns the business rule AND the security decision together) → `repository` (thin Spring Data interface, schema-scoped, no cross-tenant queries — RLS already restricts the connection).
3. Entities never use `@TenantId` (Hibernate) — tenant isolation is PostgreSQL RLS only, set via a GUC on the connection. See `postgres-rls-migration` skill.
4. Every command handler runs in exactly one transaction (`@Transactional` at the service method, not the controller).
5. Cross-module communication is by Spring Modulith domain events (`@ApplicationModuleListener`), never direct service-to-service calls across module packages.
6. After any change: run `./mvnw -f backend test` — the Modulith `verify()` architecture test must pass.
