#!/usr/bin/env python3
"""Structural methodology checks; PASS is not human semantic approval."""
from __future__ import annotations
import argparse, re, sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
ACTOR_TYPES={"human_role","external_organization","external_software_system","external_service_identity","time_or_scheduler_when_justified"}
PROFILES={"OUTLINE","ESSENTIAL","FULLY_DRESSED"}; REVIEW={"NOT_REVIEWED","AUTHOR_REVIEWED","CROSS_FUNCTIONAL_REVIEWED","DOMAIN_EXPERT_REVIEWED","STAKEHOLDER_CONFIRMED"}
ATAM={"NOT_APPLICABLE","ATAM_INSPIRED_DESK_REVIEW","STAKEHOLDER_TRADEOFF_WORKSHOP","FULL_ATAM"}

def main():
 p=argparse.ArgumentParser(); p.add_argument("path", nargs="?", default="docs/requirements/use-cases"); a=p.parse_args(); errors=warnings=0
 for path in sorted((ROOT/a.path).glob("*.md")):
  text=path.read_text(); enforced="methodology_assurance: ENFORCED" in text
  if not enforced: continue
  def fail(code,msg):
   nonlocal errors; errors+=1; print(f"ERROR [{code}] [STRUCTURAL] {path.relative_to(ROOT)}: {msg}")
  for key,values in (("primary_actor_type",ACTOR_TYPES),("detail_profile",PROFILES),("collaboration_status",REVIEW),("architecture_evaluation_type",ATAM)):
   m=re.search(rf"^{key}:\s*(\S+)",text,re.M)
   if not m or m.group(1) not in values: fail("MA-001",f"invalid {key}")
  if "system_of_interest:" not in text or "actor_goal:" not in text or "goal_level:" not in text: fail("MA-002","missing system/goal metadata")
  if re.search(r"primary_actor:\s*(payment-lifecycle|ingress|ISO-adapter|evidence-audit)",text): fail("MA-003","internal Debina module used as actor")
  name=re.search(r"^name:\s*(.+)$",text,re.M)
  if name and re.search(r"\b(processor|controller|repository|service)\b",name.group(1),re.I): fail("MA-008","use-case name describes an implementation component rather than an actor goal")
  steps=set(re.findall(r"^(?:BF|AF|CF|FF)-([0-9]+[A-Z]?)",text,re.M))
  for entry in re.findall(r"At (?:BF|AF|CF|FF)-([0-9]+[A-Z]?)",text):
   if entry not in steps: fail("MA-004",f"extension entry {entry} does not resolve")
  if "material_questions_open: true" in text and "readiness: READY" in text: fail("MA-005","material question cannot be READY")
  if "origin: AI_DRAFT" in text and "collaboration_status: STAKEHOLDER_CONFIRMED" in text and "review_evidence: []" in text: fail("MA-006","false collaboration claim")
  if "architecture_evaluation_type: FULL_ATAM" in text and "atam_stakeholder_evidence:" not in text: fail("MA-007","FULL_ATAM needs stakeholder evidence")
  if re.search(r"^slice_type:\s*(REALIZATION|TEST_WORK|TECHNICAL_TASK)\b",text,re.M): fail("MA-009","non-behavioral slice type is not a Use-Case 2.0 slice")
  if "external_claim: true" in text and re.search(r"^source_authority:\s*(implementation|java|database|project_adr)\b",text,re.M): fail("MA-010","external claim uses project implementation authority")
 print(f"SUMMARY [STRUCTURAL] errors={errors} warnings={warnings}; human methodology review remains required")
 return 1 if errors else 0
if __name__=="__main__": sys.exit(main())
