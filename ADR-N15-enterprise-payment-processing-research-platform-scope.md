# ADR-N15 — Enterprise Payment Processing Research Platform Scope

## Status

Accepted (supersedes only the Playwright-first product-scope clauses of ADR-N9 and root `AGENTS.md`).

## Context

ADR-N9 correctly established the synthetic/non-claim boundary, but its learning-first scope prevents standards-backed domain and operational work with value beyond a new browser-testing lesson.

## Options

1. Retain Playwright-first scope — rejects legitimate domain and architecture value.
2. Treat Debina as a real rail participant — unsupported and unsafe.
3. Adopt an enterprise payment-processing research-platform scope — selected.

## Decision

Debina is a synthetic, enterprise-grade SEPA/ISO 20022 payment-processing research platform. It remains neither a bank, CSM, designated system, certified payment hub, production integration, nor a source of legal or regulatory-compliance claims. Enterprise payment fidelity and standards-backed realism are product goals. Playwright is one validation layer, not the product purpose; a feature may be delivered for domain, architecture, integration, reliability, or operational value without a new Playwright lesson.

Do not blindly reproduce inaccessible participant-only behaviour. Use public authoritative material where available and otherwise record `[ASSUMPTION]` (statement, owner, evidence gap, confidence, review trigger, and removal/confirmation condition) or `[OPEN-QUESTION]`.

An aggregate is admitted only when it has independent identity, lifecycle, commands, invariants, transactional consistency boundary, persistence requirement, and failure/recovery semantics; see `docs/domain/AGGREGATE-ADMISSION-RULES.md`.

## Consequences

- ADR-N9's synthetic/non-claims clauses remain binding; only its Playwright-first scope is superseded.
- Source authority, use-case delivery, semantic governance, and evolutionary modular-monolith methods become mandatory before feature expansion resumes.
- Existing frozen payment, security, ownership, finality, LedgerPort, RLS, and status-axis decisions remain unchanged.

## Lifecycle metadata

Confidence: high. Review trigger: an accepted ADR changing the synthetic boundary. Affected use cases: all. Affected quality attributes: fidelity, testability, traceability. Reversibility: product-governance change; reverse only by superseding ADR.
