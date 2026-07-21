#!/usr/bin/env python3
"""Structural, not visual, assurance for reviewed C4-compatible dynamic views."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REVIEW = ROOT / "docs/governance/methodology-assurance/C4-DYNAMIC-REVIEW.yaml"


def main() -> int:
    errors = 0
    diagrams = sorted((ROOT / "docs/architecture/dynamic").glob("*.mmd"))
    text = REVIEW.read_text() if REVIEW.is_file() else ""
    if not text:
        print(f"ERROR [STRUCTURAL] C4-001 {REVIEW.relative_to(ROOT)} review catalogue missing")
        return 1
    for diagram in diagrams:
        name = diagram.name
        if name not in text:
            errors += 1
            print(f"ERROR [REFERENTIAL] C4-002 {name} no review record")
            continue
        block = text[text.index(name):]
        if not re.search(r"use_case_id:\s*UC-", block) or not re.search(r"diagram_type:\s*", block) or not re.search(r"scope:\s*", block):
            errors += 1
            print(f"ERROR [STRUCTURAL] C4-003 {name} missing use-case, type, or scope review metadata")
        if "sequenceDiagram" not in diagram.read_text():
            errors += 1
            print(f"ERROR [STRUCTURAL] C4-004 {name} Mermaid dynamic declaration missing")
    print(f"SUMMARY [STRUCTURAL] errors={errors} reviewed_dynamic_views={len(diagrams)} visual readability remains HUMAN-REVIEW-REQUIRED")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
