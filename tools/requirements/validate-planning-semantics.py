#!/usr/bin/env python3
from __future__ import annotations
import re, sys
from validation_common import ROOT, diagnostic, finish, list_value, story_blocks

def main():
    errors = warnings = 0; stories = list(story_blocks())
    for path, epic, story, block, meta in stories:
        status = meta.get("status", "not-started").strip("*"); subject=f"{epic}/{story}"; level=meta.get("semantic_enforcement", "LEGACY")
        unchecked = "- [ ]" in block
        hard = level in {"PILOT", "ENFORCED"}
        def report(code, message):
            nonlocal errors, warnings
            if hard: errors += 1; diagnostic("ERROR", code, path.relative_to(ROOT), subject, level, message)
            else: warnings += 1; diagnostic("WARNING", code, path.relative_to(ROOT), subject, level, message)
        if status == "done" and unchecked: report("PS-001", "done story has unchecked task")
        if status == "done" and re.search(r"\b(?:Blocker|blocked|NOT RUN)\b", block, re.I): report("PS-002", "done story contains unresolved blocker/current NOT RUN marker")
        if status == "blocked" and not (meta.get("blocker_classification") or re.search(r"\b(?:Blocker|\[[A-Z-]*BLOCKED\])", block, re.I)): report("PS-003", "blocked story lacks blocker classification")
        if status == "in-progress" and unchecked and not re.search(r"`verify:\s*[^`]+`", block):
            errors += 1; diagnostic("ERROR", "PS-004", path.relative_to(ROOT), subject, level, "in-progress story has no executable verify")
        if level in {"PILOT", "ENFORCED"} and not meta.get("use_case_slices"):
            errors += 1; diagnostic("ERROR", "PS-005", path.relative_to(ROOT), subject, level, "trace required")
    # index count must match authoritative inventory count, not historic prose.
    inv = __import__('json').loads((ROOT/'planning/story-inventory.json').read_text())
    if inv.get('epic_count') != len(list((ROOT/'planning/epics').glob('EPIC-*.md'))):
        errors += 1; diagnostic("ERROR", "PS-006", "planning/story-inventory.json", "inventory", "generated-count", "epic count differs from files")
    return finish(errors, warnings, validated_stories=len(stories))
if __name__ == "__main__": sys.exit(main())
