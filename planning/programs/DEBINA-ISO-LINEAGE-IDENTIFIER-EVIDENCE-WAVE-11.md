# DEBINA ISO Lineage Identifier Evidence — Wave 11

## Checkpoint 1 — implementation, not completion (2026-07-21)

- Baseline: `1a35cda93b4d4e23b3d11b6ffcac93e116f9f8a4`.
- Source evidence: `sepa-nexus-message-flow-and-data-blueprint.md` blob `f8667131109858da5ff7f3d3a92d74d31a1df900` (§§2.2a, 3.6, 3.8, 4.3/4.3c, 6.6, 7.2); `ADR-N3` `d5921baf873e53c2041f9e52724250ec5139f104`; `ADR-N7` `e105cd04fcba24ff882d7f03556ff4aa52643499`; EPIC-26 `bb89baffd30afcc7b75385fd466e68440d37dd19`.
- Story 26.4 was analytically `READY`: its declared GraphQL blocker was superseded by Waves 9/10. Its formal state is now `in-progress` / `IMPLEMENTED-BUT-UNVERIFIED`.

## Implemented surface

- `PaymentIsoEvidenceQuery` is the GraphQL adapter's only ISO dependency. `IsoPaymentEvidenceReadModel` queries only `iso.*`; `PaymentVisibilityQuery` delegates payment tenant/branch RLS visibility through a public payment port before ISO facts are read.
- Exposed facts: ISO message UUID, type, source version effective date, lineage role/timestamp, controlled `MSG_ID`, `PMT_INF_ID`, `INSTR_ID`, `END_TO_END_ID`, `TX_ID`, and `UETR` rows. Null source values are omitted; no value is generated. Ordering is lineage `created_at`, ISO message UUID, lineage UUID.
- GraphQL adds Query-only `paymentIsoEvidence`; Mutation and Subscription remain absent. BFF adds only the fixed `PaymentIsoEvidence` operation. Evidence Drawer loads ISO evidence independently through `/api/graphql` and renders message/identifier tables with loading, empty, error and unauthorized states.

## Evidence so far

- RED: `PaymentLineageGraphQLTest` failed because `paymentIsoEvidence` was absent.
- GREEN: focused GraphQL suite: 7 tests, 0 failures/errors/skips.
- Mutation proof: temporary `Mutation { wave11Probe: Boolean }` made `GraphQLReadOnlyStructureTest` fail; it was removed immediately.
- Frontend: codegen ran twice; lint passed with the pre-existing TanStack warning only; typecheck and production build passed.

## Remaining mandatory proof

- Add Testcontainers query tests for JSON_DIRECT/pain.001 field fidelity, deterministic ordering, tenant/branch/empty-context denial and role authorization.
- Run live Keycloak+BFF+Spring+isolated PostgreSQL JSON_DIRECT and signed pain.001 proofs; run two full backend regressions, governance validators, database review and cleanup.
- No migration is added (`N/A`): this read uses existing source tables and indexes. Correlation is intentionally not exposed because no payment-scoped source-ready runtime evidence was verified.
