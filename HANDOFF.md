# HANDOFF

## Zadanie

SEPA Nexus is a synthetic payment-testing platform. Wave 11 is extending Payment Detail's existing Evidence Drawer with source-owned ISO message lineage and identifiers through Query-only GraphQL and the Next.js BFF.

## Zrobione

- Wave 11 began at clean SHA `1a35cda93b4d4e23b3d11b6ffcac93e116f9f8a4`. Story 26.4's old “GraphQL does not exist” blocker was reclassified as stale because Wave 9/10 supply GraphQL, codegen, BFF and the drawer; it is now formally `in-progress` and analytically `IMPLEMENTED-BUT-UNVERIFIED` in `planning/epics/EPIC-26-iso-message-lineage-core.md`.
- Added `PaymentIsoEvidenceQuery` and the ISO-only `IsoPaymentEvidenceReadModel`; payment tenant/branch object visibility is checked only through the new public `PaymentVisibilityQuery`, not a foreign repository. Query-only GraphQL, BFF allowlist, generated TypeScript and independent Evidence Drawer ISO tables are implemented.
- RED/GREEN evidence: missing-field structural test first failed; focused GraphQL suite then passed 7/7. A temporary Mutation root correctly made the read-only structural test fail and was removed. Frontend codegen ran twice; lint (known unchanged TanStack warning), typecheck and build passed.
- Durable state is in `planning/programs/DEBINA-ISO-LINEAGE-IDENTIFIER-EVIDENCE-WAVE-11.md` and `/tmp/DEBINA-ISO-LINEAGE-IDENTIFIER-EVIDENCE-WAVE-11/checkpoint-1.md`.

## Utknęliśmy na

Wave 11 is not complete: no Testcontainers ISO read/security test has yet proven JSON_DIRECT/pain.001 facts, ordering, tenant/branch and empty-GUC behaviour; the real Keycloak+BFF+Spring+isolated PostgreSQL runtime and two full backend regressions are also unrun. Do not call Story 26.4 done or broaden the ISO schema/roles to make tests easier.

## Plan na następny krok

Open the Wave 11 program record, add a PostgreSQL Testcontainers integration test for `PaymentIsoEvidenceQuery` that creates both JSON_DIRECT and signed pain.001 source rows and proves tenant/branch visibility before starting the live runtime proof.

## Pułapki, których nie wolno powtórzyć

- `iso.iso_messages` has tenant but no branch column. Preserve branch isolation through the public payment visibility port; never add a GraphQL repository shortcut or broad ISO grant without an accepted owner decision.
- `TX_ID` exists structurally but no current pain.001 channel populates it; `UETR` and `INSTR_ID` are optional. Do not synthesize them or render absent values as facts.
- Spring Modulith test execution rewrites `build/generated-spring-modulith/javadoc.json`; restore it before committing if it changes incidentally.
- Preserve the Query-only allowlist boundary: browser posts only operation name/variables to `/api/graphql`; no raw message payload, token exposure, Mutation or Subscription.
