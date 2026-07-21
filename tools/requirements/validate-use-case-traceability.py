#!/usr/bin/env python3
from __future__ import annotations
import re, sys
from validation_common import ROOT, diagnostic, finish, ids_from_catalog, list_value, source_ids, story_blocks

def main():
    errors = warnings = 0
    catalog = ROOT / "docs/requirements/USE-CASE-CATALOG.yaml"
    rules = ids_from_catalog(ROOT / "docs/requirements/BUSINESS-RULE-CATALOG.yaml", "BR")
    source = source_ids(); text = catalog.read_text()
    ucs = re.findall(r"\bid:\s*(UC-[A-Z0-9-]+)", text); slices = re.findall(r"\bslices:\s*\[([^]]*)\]", text)
    flat_slices = [s for value in slices for s in list_value(value)]
    for kind, values in (("use case", ucs), ("slice", flat_slices)):
        if len(values) != len(set(values)):
            errors += 1; diagnostic("ERROR", "UCT-001", catalog, kind, "unique-id", "duplicate ID")
    for uc in ucs:
        doc_match = re.search(rf"id:\s*{re.escape(uc)}.*?document:\s*(\S+)", text, re.S)
        if not doc_match or not (ROOT / doc_match.group(1)).is_file():
            errors += 1; diagnostic("ERROR", "UCT-002", catalog, uc, "document", "document does not resolve"); continue
        doc = (ROOT / doc_match.group(1)).read_text()
        for field in ("Goal", "Primary actor", "Main success scenario", "Source references", "## Slices"):
            if field not in doc:
                errors += 1; diagnostic("ERROR", "UCT-003", doc_match.group(1), uc, field, "mandatory use-case content missing")
    for path, epic, story, block, meta in story_blocks():
        level = meta.get("semantic_enforcement", "LEGACY")
        traces = list_value(meta.get("use_case_slices"))
        required = level in {"PILOT", "ENFORCED"}
        if required and not traces:
            errors += 1; diagnostic("ERROR", "UCT-004", path.relative_to(ROOT), f"{epic}/{story}", level, "required use-case slice missing")
        elif not required and not traces:
            warnings += 1; diagnostic("WARNING", "UCT-004", path.relative_to(ROOT), f"{epic}/{story}", level, "legacy-untraced")
        for trace in traces:
            if trace not in flat_slices:
                errors += 1; diagnostic("ERROR", "UCT-005", path.relative_to(ROOT), f"{epic}/{story}", level, f"unknown slice {trace}")
        for rule in list_value(meta.get("business_rules")):
            if rule not in rules:
                errors += 1; diagnostic("ERROR", "UCT-006", path.relative_to(ROOT), f"{epic}/{story}", level, f"unknown business rule {rule}")
        for ref in list_value(meta.get("source_references")):
            if ref not in source:
                errors += 1; diagnostic("ERROR", "UCT-007", path.relative_to(ROOT), f"{epic}/{story}", level, f"unknown source {ref}")
    return finish(errors, warnings, validated_use_cases=len(ucs), validated_slices=len(flat_slices), validated_stories=sum(1 for _ in story_blocks()))
if __name__ == "__main__": sys.exit(main())
