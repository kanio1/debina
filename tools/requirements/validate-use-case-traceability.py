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
        document = doc_match.group(1).rstrip('}') if doc_match else None
        if not doc_match or not (ROOT / document).is_file():
            errors += 1; diagnostic("ERROR", "UCT-002", catalog, uc, "document", "document does not resolve"); continue
        doc = (ROOT / document).read_text()
        enforced = bool(re.search(r"\benforcement:\s*ENFORCED", doc))
        for field in (("# ", "## Main success scenario") if enforced else ("Goal", "Primary actor", "Main success scenario", "Source references", "## Slices")):
            if field not in doc:
                errors += 1; diagnostic("ERROR", "UCT-003", document, uc, str(field), "mandatory use-case content missing")
        if enforced:
            required = {"narrative":"cockburn_fully_dressed", "decomposition":"use_case_2_0", "rule_elaboration":"example_mapping", "payment_process_model":"iso_20022_business_process_catalogue", "domain_modeling":"domain_driven_design", "architecture_views":"c4", "quality_requirements":"arc42_quality_scenarios", "architecture_evaluation":"atam_lite"}
            for key, value in required.items():
                if not re.search(rf"{key}:\s*{value}\b", doc):
                    errors += 1; diagnostic("ERROR", "UCT-008", document, uc, "methodology", f"missing/unsupported {key}")
            if not re.search(r"^1\.\s", doc, re.M):
                errors += 1; diagnostic("ERROR", "UCT-009", document, uc, "main-flow", "numbered main success scenario missing")
    backlog_map = ROOT / "docs/requirements/USE-CASE-TO-BACKLOG-MAP.yaml"
    if backlog_map.exists():
        for value in re.findall(r"\bslices:\s*\[([^]]*)\]", backlog_map.read_text()):
            for slice_id in list_value(value):
                if slice_id not in flat_slices:
                    errors += 1; diagnostic("ERROR", "UCT-010", backlog_map, slice_id, "backlog-map", "slice does not resolve")
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
