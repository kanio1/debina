#!/usr/bin/env python3
from __future__ import annotations
import re, sys
from validation_common import ROOT, diagnostic, finish, source_ids

KNOWN={"EU-LAW","EPC-SCT","EPC-SCT-INST","ISO20022","TIPS","STEP2","RT1","STET","PROJECT-ADR","PROJECT-SIMULATION","ASSUMPTION","OPEN-QUESTION","PARTICIPANT-DOCUMENTATION-REQUIRED"}
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
    return finish(errors,warnings,validated_sources=len(src))
if __name__ == '__main__': sys.exit(main())
