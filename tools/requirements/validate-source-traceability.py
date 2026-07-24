#!/usr/bin/env python3
from __future__ import annotations
import re, sys
from validation_common import ROOT, diagnostic, finish, ids_from_catalog, list_value, source_ids

KNOWN={"EU-LAW","EPC-SCT","EPC-SCT-INST","ISO20022","TIPS","STEP2","RT1","STET","PROJECT-ADR","PROJECT-SIMULATION","ASSUMPTION","OPEN-QUESTION","PARTICIPANT-DOCUMENTATION-REQUIRED"}
RAIL_STATUS={"APPLICABLE","APPLICABLE-WITH-RAIL-EXTENSION","NOT-APPLICABLE","PROJECT-SIMULATION","SOURCE-BLOCKED","PARTICIPANT-DOCUMENTATION-REQUIRED"}
def main():
    errors=warnings=0; src=source_ids(); paths=[ROOT/'docs/requirements/BUSINESS-RULE-CATALOG.yaml', ROOT/'docs/requirements/USE-CASE-CATALOG.yaml']
    for path in paths:
        text=path.read_text()
        for tag in re.findall(r"\[([A-Z0-9-]+)\]", text):
            if tag not in KNOWN and not tag.startswith(("UC-", "UCS-", "BR-", "QS-", "EPIC-")):
                errors+=1; diagnostic("ERROR","SRC-001",path.relative_to(ROOT),tag,"known-source-tag","unknown tag")
        for ref in re.findall(r"source_references:\s*\[([^]]*)\]", text):
            for item in [x.strip() for x in ref.split(',') if x.strip()]:
                if item not in src:
                    errors+=1; diagnostic("ERROR","SRC-002",path.relative_to(ROOT),item,"source-registry","reference missing")
    rules=(ROOT/'docs/requirements/BUSINESS-RULE-CATALOG.yaml').read_text()
    if re.search(r"id:\s*BR-",rules) and 'source_references:' not in rules:
        errors+=1; diagnostic("ERROR","SRC-003","BUSINESS-RULE-CATALOG.yaml","rules","external-rule-source","missing source references")
    for path in ROOT.glob("docs/requirements/use-cases/*.md"):
        for value in re.findall(r"rail_applicability:\s*\[([^]]*)\]", path.read_text()):
            for item in [x.strip() for x in value.split(',') if x.strip()]:
                if item not in RAIL_STATUS:
                    errors += 1; diagnostic("ERROR", "SRC-004", path.relative_to(ROOT), item, "rail-applicability", "unsupported value")
    evidence = ROOT / 'docs/standards/SOURCE-EVIDENCE-CATALOG.yaml'
    if not evidence.is_file():
        errors += 1; diagnostic("ERROR", "SRC-005", evidence.relative_to(ROOT), "catalog", "source-evidence", "missing source evidence catalogue")
    else:
        content = evidence.read_text()
        known_rules = ids_from_catalog(
            ROOT / "docs/requirements/BUSINESS-RULE-CATALOG.yaml", "BR"
        )
        required_fields = (
            "source_registry_id", "document_title", "publisher", "document_version",
            "publication_date", "effective_date", "section", "rail",
            "public_or_restricted", "access_date", "supported_claim",
            "project_interpretation", "applicable_rules", "confidence",
        )
        blocks = re.split(r"(?=^\s*- id:\s*SE-)", content, flags=re.M)
        for block in blocks:
            identifier = re.search(r"^\s*- id:\s*(SE-[A-Z0-9-]+)", block, re.M)
            if not identifier:
                continue
            evidence_id = identifier.group(1)
            for field in required_fields:
                if not re.search(rf"^\s*{field}:\s*\S", block, re.M):
                    errors += 1
                    diagnostic(
                        "ERROR", "SRC-006", evidence.relative_to(ROOT), evidence_id,
                        "source-evidence", f"mandatory field {field} missing",
                    )
            source = re.search(r"^\s*source_registry_id:\s*([^\s]+)", block, re.M)
            if source and source.group(1) not in src:
                errors += 1
                diagnostic(
                    "ERROR", "SRC-007", evidence.relative_to(ROOT), source.group(1),
                    "source-evidence", "source registry reference missing",
                )
            linked_rules = re.search(r"^\s*applicable_rules:\s*\[([^]]*)\]", block, re.M)
            for rule in list_value(linked_rules.group(1) if linked_rules else ""):
                if rule not in known_rules:
                    errors += 1
                    diagnostic(
                        "ERROR", "SRC-008", evidence.relative_to(ROOT), evidence_id,
                        "per-claim-evidence", f"unknown applicable rule {rule}",
                    )
    print("ASSURANCE [REFERENTIAL] source registry and evidence-catalogue references were checked; semantic truth requires source review.")
    return finish(errors,warnings,validated_sources=len(src))
if __name__ == '__main__': sys.exit(main())
