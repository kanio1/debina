# Research: Markdown Single-H1 Checker

**Date**: 2026-07-29

## Decision: ATX-only H1 detection

**Rationale**: Pilot scope; Setext adds parser complexity without pilot value.

**Alternatives considered**: CommonMark full parser (rejected — over-engineering); regex-only without fence awareness (rejected — false positives).

## Decision: Fenced block state machine

**Rationale**: Simple line scanner toggling `in_fence` on opening/closing ``` or ~~~ lines.

**Alternatives considered**: Full markdown AST (rejected).

## Decision: UTF-8 only

**Rationale**: Project standard; decode errors surface as ERROR.

## Decision: Exit code precedence

**Rationale**: ERROR (2) over FAIL (1) when mixed — conservative for automation.
