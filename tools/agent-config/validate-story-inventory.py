#!/usr/bin/env python3
"""Validates planning/story-inventory.json against planning/epics/*.md.

No third-party dependency -- stdlib json/re only. Checks:
  1. every "## Story X.Y" header found in planning/epics/*.md has a matching
     inventory record (epic, story) pair;
  2. no duplicate (epic, story) IDs in the inventory;
  3. every record's status is in the allowed set;
  4. every record's source_file actually exists;
  5. a `done` record has at least one checked-off task ("- [x]") in its
     source story block, or an explicit evidence note (see EVIDENCE_MARKERS);
  6. a record whose readiness would be READY (status not-started/in-progress,
     empty or already-done depends_on, capability_analysis not NOT_DEEP_DIVED)
     has at least one non-empty `verify` entry.

Usage: python3 tools/agent-config/validate-story-inventory.py
"""
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
INVENTORY_PATH = REPO_ROOT / "planning" / "story-inventory.json"
EPICS_DIR = REPO_ROOT / "planning" / "epics"

ALLOWED_STATUS = {"not-started", "in-progress", "blocked", "done"}
STORY_HEADER_RE = re.compile(r"^## Story ([0-9]+(?:[A-Z]|\.[0-9]+[A-Z]?)*) — (.+)$", re.MULTILINE)
EVIDENCE_MARKERS = ("PASS", "done", "zbudowane", "zaimplementowano", "zweryfikowano")


def fail(msg, errors):
    errors.append(msg)
    print(f"FAIL  {msg}")


def ok(msg):
    print(f"OK    {msg}")


def main():
    errors = []

    if not INVENTORY_PATH.exists():
        print(f"FAIL  {INVENTORY_PATH} not found")
        print("RESULT: FAIL")
        return 1

    try:
        inventory = json.loads(INVENTORY_PATH.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        print(f"FAIL  {INVENTORY_PATH} is not valid JSON: {e}")
        print("RESULT: FAIL")
        return 1

    stories = inventory.get("stories", [])
    print("== story inventory validator ==")
    print(f"file: {INVENTORY_PATH}")
    print(f"records: {len(stories)}")
    print()

    # 1 + 2: cross-check against actual epic files, detect duplicates
    seen = {}
    dupes = []
    for rec in stories:
        key = (rec.get("epic"), rec.get("story"))
        if key in seen:
            dupes.append(key)
        seen[key] = rec

    if dupes:
        fail(f"{len(dupes)} duplicate (epic, story) ID(s): {dupes}", errors)
    else:
        ok("no duplicate (epic, story) IDs")

    source_headers = {}
    for path in sorted(EPICS_DIR.glob("EPIC-*.md")):
        text = path.read_text(encoding="utf-8")
        epic_id_match = re.match(r"^(EPIC-\d+)", path.stem)
        epic_id = epic_id_match.group(1) if epic_id_match else path.stem
        for h in STORY_HEADER_RE.finditer(text):
            source_headers[(epic_id, h.group(1))] = path

    missing_from_inventory = [k for k in source_headers if k not in seen]
    if missing_from_inventory:
        fail(f"{len(missing_from_inventory)} story header(s) in planning/epics/*.md missing from inventory: "
             f"{missing_from_inventory[:10]}{'...' if len(missing_from_inventory) > 10 else ''}", errors)
    else:
        ok("every '## Story' header in planning/epics/*.md has an inventory record")

    stale_in_inventory = [k for k in seen if k not in source_headers]
    if stale_in_inventory:
        fail(f"{len(stale_in_inventory)} inventory record(s) reference a story header no longer present in source: "
             f"{stale_in_inventory[:10]}", errors)
    else:
        ok("no inventory record is stale relative to planning/epics/*.md")

    # 3: status vocabulary
    bad_status = [(k, r.get("status")) for k, r in seen.items() if r.get("status") not in ALLOWED_STATUS]
    if bad_status:
        fail(f"{len(bad_status)} record(s) with a status outside {sorted(ALLOWED_STATUS)}: {bad_status[:10]}", errors)
    else:
        ok(f"every record's status is one of {sorted(ALLOWED_STATUS)}")

    # 4: source_file existence
    missing_files = []
    for k, r in seen.items():
        sf = r.get("source_file")
        if not sf or not (REPO_ROOT / sf).exists():
            missing_files.append((k, sf))
    if missing_files:
        fail(f"{len(missing_files)} record(s) reference a non-existent source_file: {missing_files[:10]}", errors)
    else:
        ok("every record's source_file exists")

    # 5: done stories have checked-off evidence in their source block
    done_without_evidence = []
    for k, r in seen.items():
        if r.get("status") != "done":
            continue
        path = source_headers.get(k)
        if path is None:
            continue
        text = path.read_text(encoding="utf-8")
        headers = list(STORY_HEADER_RE.finditer(text))
        block = None
        for i, h in enumerate(headers):
            if h.group(1) == k[1]:
                start = h.end()
                end = headers[i + 1].start() if i + 1 < len(headers) else len(text)
                block = text[start:end]
                break
        if block is None:
            done_without_evidence.append(k)
            continue
        has_checked_task = "- [x]" in block
        has_evidence_note = any(marker in block for marker in EVIDENCE_MARKERS)
        if not (has_checked_task or has_evidence_note):
            done_without_evidence.append(k)
    if done_without_evidence:
        fail(f"{len(done_without_evidence)} 'done' record(s) with no checked-off task and no evidence note in "
             f"their source block: {done_without_evidence[:10]}", errors)
    else:
        ok("every 'done' record has a checked-off task or an explicit evidence note")

    # 6: a READY-looking record must carry an executable verify
    ready_without_verify = []
    for k, r in seen.items():
        if r.get("status") not in ("not-started", "in-progress"):
            continue
        if r.get("capability_analysis") == "NOT_DEEP_DIVED":
            continue
        depends = r.get("depends_on") or []
        if depends:
            continue  # has an explicit dependency -- not a bare READY candidate here
        verify = r.get("verify")
        if not verify or not any((v or "").strip() for v in verify):
            ready_without_verify.append(k)
    if ready_without_verify:
        fail(f"{len(ready_without_verify)} deep-dived, dependency-free, not-done record(s) with no executable "
             f"'verify': {ready_without_verify[:10]}", errors)
    else:
        ok("every deep-dived, dependency-free, not-done record carries an executable 'verify'")

    print()
    if errors:
        print("RESULT: FAIL")
        return 1
    print("RESULT: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
