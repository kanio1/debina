---
name: source-backed-payments-modeling
description: Model or review Debina payment-domain terminology, ISO 20022 and EPC concepts, rail-specific terminology, business orders, instructions, transactions, groups, files, submissions, messages, clearing, settlement, statuses, returns, recalls, cases, evidence, audit, or aggregate candidates with source authority and rail-leakage controls. Do not use for generic XML, unrelated JSON APIs, or generic migrations.
---

# Source-Backed Payments Modeling

## Purpose

Validate semantics used by a use case or planning change without converting external nouns into Debina aggregates.

## Read first

Read `SOURCE-AUTHORITY-MATRIX.md`, `SOURCE-REGISTRY.yaml`, payment concept catalogue/model, rail concept matrix, rail-specific modeling, aggregate admission rules, terminology glossary, applicable ADRs and the use case.

## Workflow

1. Identify the concept and authority domain; inspect its registry source, per-claim source-evidence record and rail applicability.
2. Compare the rail concept matrix with the current Debina representation; identify terminology collisions and common-core rail leakage.
3. Classify the result as existing representation, new value object, entity, aggregate candidate, technical artifact, rail-specific concept, or unsupported assumption.
4. Apply aggregate admission criteria before recommending an aggregate.
5. Return one of `SOURCE-CONFIRMED`, `PROJECT-INTERPRETATION`, `PROJECT-SIMULATION`, `RAIL-SPECIFIC`, `INSUFFICIENT-EVIDENCE`, `CONFLICTING-SOURCES`, or `PARTICIPANT-DOCUMENTATION-REQUIRED` and update traceability/gaps.

## Handoff and evidence

Receive actor goals and candidate rules from `enterprise-use-case-engineering`; return source classification and terminology/aggregate findings to it. Do not treat a Java class, database status or project ADR as authority for EPC, ISO or rail behavior. Record detailed support using [the source-evidence policy](../../../docs/governance/methodology-assurance/SOURCE-EVIDENCE-POLICY.md), then hand architecture realization candidates to `architecture-evolution-review`.

## Guardrails

Keep business order, payment instruction, transaction, group, batch, bulk, file, submission, interchange, business/message/file envelope, clearing, settlement, status, return, recall, case, evidence and audit distinct. Never infer participant behaviour or rail processing. Do not recommend an aggregate because a noun exists in a rulebook.

## Validation and example

Run source and use-case validators. Example: `ApprovalDecision` is a project-policy business record under BP-03, not an SCT rail concept; its supporting audit entry is a technical-and-audit artifact owned by evidence-audit.
