# ADR-N16 — Playwright Validation Sequencing

## Status

Accepted

## Context

Existing sequencing defers browser acceptance until multiple screens exist. ADR-N15 makes Playwright a validation layer rather than the product purpose.

## Options

1. Keep all browser automation deferred — leaves the early operational path without a browser-level confidence check.
2. Start broad acceptance coverage now — conflicts with the approved feature-expansion pause and unstable feature catalogue.
3. Permit a narrow smoke while deferring full acceptance — selected.

## Decision

Minimal smoke may run before feature completeness: Keycloak login, BFF session, health/runtime availability, one JSON payment submission, one maker-checker approval, and one Payment Detail load. Full Playwright acceptance remains deferred until the majority of features are complete and the required use-case slices/read models are stable. This ADR does not implement Playwright or remove existing Playwright stories; reclassification follows after acceptance through backlog migration.

## Consequences

- The six named smoke journeys are the maximum early browser scope; they do not become a general acceptance gate.
- Existing Playwright stories remain historical backlog records until Phase F performs a source-backed reclassification.
- No frontend, runtime, or CI implementation is authorized by this ADR alone.

## Lifecycle metadata

Confidence: high. Review trigger: the Phase D smoke catalogue and later acceptance-readiness review. Affected use cases: authentication, submit, approval, detail read. Quality attributes: testability and operational confidence. Reversibility: sequencing can be superseded without changing payment semantics.
