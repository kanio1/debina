# Product Vision

Debina is a synthetic, enterprise-grade SEPA/ISO 20022 payment-processing research platform. It maximizes fidelity to public standards and official rail documentation while making no claim of regulatory certification, scheme certification, central-bank integration, production readiness, or live CSM participation. [PROJECT-ADR: project-adr-n10; PROJECT-ADR: ADR-N15]

## Purpose and boundary

It exists to make payment-domain behaviour, ownership, failure and recovery semantics inspectable and testable. It models payment initiation, ISO message lineage, approval, routing, settlement, liquidity, ledger accounting, finality, delivery, evidence, audit and operational investigation. It simulates customer channels, external participants and CSM interactions unless a future, separately evidenced integration says otherwise.

It is not a bank, PSP, CSM, settlement system, certification artefact, regulatory-compliance assertion, or an implementation of participant-only rulebooks. Rail differences are retained in explicit profiles/adapters; unavailable details are `UNKNOWN` or `PARTICIPANT-DOCUMENTATION-REQUIRED`.

## Product goals

| Goal | Product intent |
|---|---|
| Fidelity | Source-backed ISO/EPC/rail terminology, evidence and rail applicability |
| Enterprise architecture | Explicit contexts, schema ownership, ports and failure boundaries |
| Integrity and correctness | LedgerPort-only money movement, idempotency and explicit finality |
| Security and tenancy | Least privilege, RLS where owned data requires it, BFF session boundary |
| Integration and recovery | Public contracts, outbox/inbox, correlation, replay/failure evidence |
| Operability and audit | Traceable payment, message, evidence and audit identifiers |
| Evolvability and testing | Modular-monolith boundaries, local-first verification and research experiments |

Playwright is one later validation layer, not the product purpose. Dagger is planned local, provider-neutral and Go-SDK based; no remote CI provider is selected.
