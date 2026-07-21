# HANDOFF

## Zadanie

SEPA Nexus is a synthetic payment-testing platform. Wave 11 is extending Payment Detail's existing Evidence Drawer with source-owned ISO message lineage and identifiers through Query-only GraphQL and the Next.js BFF.

## Zrobione

- Wave 11 began at clean SHA `1a35cda93b4d4e23b3d11b6ffcac93e116f9f8a4`. Story 26.4's old “GraphQL does not exist” blocker was reclassified as stale because Wave 9/10 supply GraphQL, codegen, BFF and the drawer; it is now formally `in-progress` and analytically `IMPLEMENTED-BUT-UNVERIFIED` in `planning/epics/EPIC-26-iso-message-lineage-core.md`.
- Added `PaymentIsoEvidenceQuery` and the ISO-only `IsoPaymentEvidenceReadModel`; payment tenant/branch object visibility is checked only through the new public `PaymentVisibilityQuery`, not a foreign repository. Query-only GraphQL, BFF allowlist, generated TypeScript and independent Evidence Drawer ISO tables are implemented.
- RED/GREEN evidence: missing-field structural test first failed; focused GraphQL suite then passed 7/7. A temporary Mutation root correctly made the read-only structural test fail and was removed. `IsoPaymentEvidenceQueryIntegrationTest` is 3/3 PASS on isolated PostgreSQL 18, proving JSON_DIRECT/pain.001 facts, ordering, tenant/branch/empty-context denial. `ApprovalGraphQlRuntimeTest` is 10/10 PASS, including the `payment_viewer` contract and an unrelated-role denial. Frontend codegen ran twice; lint (known unchanged TanStack warning), typecheck and build passed. Live Keycloak+BFF+Spring+isolated PostgreSQL JSON and signed pain.001 flows passed; the live probe corrected the BFF projection and exposes parser-pinned `pain.001.001.09` only for actual pain.001 rows. Focused gate is 32/32.
- Durable state is in `planning/programs/DEBINA-ISO-LINEAGE-IDENTIFIER-EVIDENCE-WAVE-11.md` and `/tmp/DEBINA-ISO-LINEAGE-IDENTIFIER-EVIDENCE-WAVE-11/checkpoint-1.md`.

## Utknęliśmy na

Wave 11 is not complete: the real Keycloak+BFF+Spring+isolated PostgreSQL runtime and two full backend regressions are unrun. Do not call Story 26.4 done or broaden the ISO schema/roles to make tests easier.

## Plan na następny krok

Inspect the Wave 10 live-runtime procedure and execute the real Keycloak+BFF+Spring+isolated PostgreSQL JSON_DIRECT then signed pain.001 proof, retaining compact logs under `/tmp/DEBINA-ISO-LINEAGE-IDENTIFIER-EVIDENCE-WAVE-11/`.

## Pułapki, których nie wolno powtórzyć

- `iso.iso_messages` has tenant but no branch column. Preserve branch isolation through the public payment visibility port; never add a GraphQL repository shortcut or broad ISO grant without an accepted owner decision.
- `TX_ID` exists structurally but no current pain.001 channel populates it; `UETR` and `INSTR_ID` are optional. Do not synthesize them or render absent values as facts.
- Spring Modulith test execution rewrites `build/generated-spring-modulith/javadoc.json`; restore it before committing if it changes incidentally.
- Preserve the Query-only allowlist boundary: browser posts only operation name/variables to `/api/graphql`; no raw message payload, token exposure, Mutation or Subscription.
