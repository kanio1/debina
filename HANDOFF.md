# HANDOFF

## Zadanie

Wave 9 dostarcza source-owned read-only GraphQL oraz maker-checker approval workspace przez Next.js BFF. Wave 8 jest zakończonym fundamentem; nie pushować.

## Zrobione

- Baseline Wave 9: `ade6218`; commity `bdb38ae` i `a62f87b` tworzą EPIC-78, Query-only SDL, publiczny `modules.ApprovalQueueQuery`, thin `graphql` Modulith adapter oraz pierwszą runtime proof.
- `ApprovalGraphQlRuntimeTest` działa na PostgreSQL 18 Testcontainers: authenticated `payment_approver` otrzymuje DTO z `/graphql`, brak bearer dostaje 401.
- Parser schema test dowodzi Query oraz braku Mutation/Subscription; mutation proof był RED, następnie schema została przywrócona GREEN.

## Utknęliśmy na

Nie ma Class C blokera. Nieukończone Wave 9: bezpośrednie testy depth/complexity i prod introspection, realne GraphQL RLS/cursor/detail, codegen, BFF proxy/commands, UI i runtime flow. Niecommitowany jest tylko GraphQL hardening (`GraphQlSecurityConfiguration`, `application-prod.yml`), który ładuje się w runtime test, ale nie ma jeszcze negatywnego testu limitów.

## Plan na następny krok

Dodaj do `ApprovalGraphQlRuntimeTest` negatywną query-depth/complexity proof i profil `prod` introspection proof dla istniejącego niecommitowanego hardeningu, uruchom targeted test, potem commit.

## Pułapki, których nie wolno powtórzyć

- Maven nadal przepisuje `build/generated-spring-modulith/javadoc.json`; zawsze odtwórz przez `apply_patch`.
- GraphQL `@SchemaMapping` nie może mieć dwóch overloadów dla tego samego schema field; bezpieczne `expiredButUnprocessed` musi być property obu DTO.
- Adapter GraphQL używa tylko root public port `com.sepanexus.modules.ApprovalQueueQuery`; port w package `.service` nie jest eksportowany przez Modulith i łamie `ApplicationModules.verify()`.
