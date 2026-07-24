# HANDOFF

## Current objective

Close the E1 BFF + Playwright tranche after a narrow Ask review of the composed-stack correction, then one coherent commit.

## Current use case

`UC-SCT-002` / `UCS-SCT-002-A` / `UCS-SCT-002-B` (customer pain.001 submission).

## Current state

- E1 backend tranche complete (committed).
- E1 BFF + Playwright tranche + composed-stack wiring implemented (not committed).
- Focused verification green: `test:pain001-upload-route`, `test:pain001-upload-card`, `generate:e1-signed-fixture`, `typecheck`, lint (changed files), `build`, Dagger pure/cmd tests.
- `git diff --check`: clean. Nothing staged.
- **Playwright composed smoke PASS (2/2):**
  1. `dagger call smoke-signed-pain-001 --lock=frozen --progress=plain` → `1 passed`
  2. `dagger call smoke-signed-pain-001 --proof-nonce=reliability-<ts> --lock=frozen --progress=plain` → `1 passed` (fresh isolated stack)
- Host-only `pnpm run test:smoke:e1-pain001` still cannot resolve `frontend`/`keycloak` aliases; that is expected. Canonical composed command is the Dagger function (also `pnpm run test:smoke:e1-pain001:composed`).

## Completed

- BFF upload adapter, UI card, focused route/card tests, deterministic TEST_ONLY lab key + V62, one accepted Playwright smoke.
- Dagger `smoke-signed-pain-001` wires the existing payment smoke runtime to `pnpm run test:smoke:e1-pain001`.
- Backend smoke services remigrate on the live bound Postgres before `spring-boot:run` so ephemeral DB restarts cannot drop `sepa_app` between marker Flyway and consumer start.

## Decisions

Educational E1 directions unchanged.

Composed-runtime corrections:

- Playwright runs inside the Dagger service network (`http://frontend:3000`, `http://keycloak:8080`); host execution is not the composed proof.
- Callable name is `smoke-signed-pain-001` (Dagger kebab of `SmokeSignedPain001`).
- Optional `--proof-nonce` isolates a fresh ephemeral stack for reliability reruns.
- Outside ADR-N16 `smoke-suite` (E1 is separately callable).

## Blocked for production

- Dated specialist approvals / review councils / canonical migration admission
- EPC TVS lawful acquisition, checksums and production validation claim
- Normative detached-signature transport contract and signer-identity authority
- Signer registry is participant/global, not tenant-scoped; tenant-bound signer authority unresolved
- IBAN check-digit, SCT EUR-only and UETR UUIDv4 syntax enforcement deferred
- XML encoding/charset acceptance profile
- Evidence retention, encryption-at-rest and legal hold
- Measured production intake limits acceptance
- External ISO/EPC conformance or settlement/clearing claims
- GraphQL/REST exposure of source/receive/record timestamps (deferred)
- iso/ingress RLS on evidence tables (pre-existing gap)
- Contract-phase drop/rename of legacy `cre_dt_tm` column
- Idempotency TTL/retention/archival policy
- Distinct `EXPIRED_KEY` / `REVOKED_KEY` profile outcomes
- Production BFF upload limits and normative signer transport contract

These are `BLOCKED_FOR_PRODUCTION` and must not block safe local educational implementation.

## Next tasks

1. Independent narrow Ask review of the Playwright/runtime correction.
2. Fix any BLOCKER/HIGH finding from that review.
3. Run final focused regression (route/card + composed smoke).
4. Create one coherent BFF + Playwright tranche commit (exclude CLAUDE files, `.cursor/**`, generated javadoc).
5. Select the next educational slice.

## Resume from here

Start task 1: independent narrow Ask review of `smoke-signed-pain-001` wiring and the live-Postgres remigrate fix.
## Follow-up improvements

- Remove unused `E1_SMOKE_PROOF_NONCE` environment variable or consume it
  explicitly; runtime isolation currently comes from the Dagger instance suffix.
- Reliability reruns must use `--proof-nonce` to avoid Dagger cache reuse.
- Add a pure topology assertion for `smoke-signed-pain-001`.