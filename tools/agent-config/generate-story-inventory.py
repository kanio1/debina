#!/usr/bin/env python3
"""Deterministically generates planning/story-inventory.json from planning/epics/*.md.

Standard library only. No capability is invented: every field is extracted from the
epic markdown files themselves.

Extraction rules:
  - "epics": one entry per planning/epics/EPIC-*.md file, epic id from the filename,
    status from that file's YAML-ish frontmatter `status:` line.
  - "stories": one entry per "## Story X.Y — Title" header (same header regex as
    tools/agent-config/validate-story-inventory.py, so the two stay in lockstep),
    with status/depends_on parsed from the "status:"/"depends_on: [...]" lines that
    follow the header, and verify commands from every backtick-quoted `verify: ...`
    segment inside that story's own block, in source order.
  - "capability_analysis" defaults to NOT_DEEP_DIVED. If a story's own block carries
    an explicit "[DONE <date>]" implementation marker, that story gets a specific
    "DEEP_DIVED: <date> <epic> Story <story> implementation session" note -- the only
    capability_analysis signal mechanically derivable from a story's own text. Earlier
    manually-curated "dependency-narrowing session" annotations on other stories were
    session provenance recorded by a human, not a textual pattern unique to those
    stories (the same marker text also appears, unevenly, in stories that were NOT so
    annotated) -- reproducing them exactly is not something this generator invents;
    it intentionally reverts those to NOT_DEEP_DIVED rather than guess.

Usage:
  python3 tools/agent-config/generate-story-inventory.py --check   # compare to repo copy, exit 1 on diff
  python3 tools/agent-config/generate-story-inventory.py --write   # overwrite planning/story-inventory.json
"""
import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
EPICS_DIR = REPO_ROOT / "planning" / "epics"
INVENTORY_PATH = REPO_ROOT / "planning" / "story-inventory.json"

STORY_HEADER_RE = re.compile(r"^## Story ([0-9]+(?:[A-Z]|\.[0-9]+[A-Z]?)*) — (.+)$", re.MULTILINE)
FRONTMATTER_RE = re.compile(r"\A---\n(.*?)\n---\n", re.DOTALL)
STATUS_LINE_RE = re.compile(r"^status:\s*(\S+)\s*$", re.MULTILINE)
DEPENDS_ON_LINE_RE = re.compile(r"^depends_on:\s*\[(.*?)\]\s*$", re.MULTILINE)
VERIFY_SEGMENT_RE = re.compile(r"`verify:\s*(.*?)`")
DONE_MARKER_RE = re.compile(r"\[DONE (\d{4}-\d{2}-\d{2})\]")
NOT_DEEP_DIVED = "NOT_DEEP_DIVED"


def epic_id_from_path(path: Path) -> str:
    match = re.match(r"^(EPIC-\d+)-", path.name)
    if not match:
        raise ValueError(f"unexpected epic filename shape: {path.name}")
    return match.group(1)


def frontmatter_status(text: str) -> str:
    match = FRONTMATTER_RE.match(text)
    if not match:
        return "not-started"
    status_match = STATUS_LINE_RE.search(match.group(1))
    return status_match.group(1) if status_match else "not-started"


def split_depends_on(raw: str) -> list[str]:
    raw = raw.strip()
    if not raw:
        return []
    return [item.strip() for item in raw.split(",") if item.strip()]


def story_block(text: str, start: int, end: int) -> str:
    return text[start:end]


def capability_analysis_for(story_text: str, epic_id: str, story_id: str) -> str:
    done_match = DONE_MARKER_RE.search(story_text)
    if done_match:
        return f"DEEP_DIVED: {done_match.group(1)} {epic_id} Story {story_id} implementation session"
    return NOT_DEEP_DIVED


def parse_epic_file(path: Path) -> tuple[dict, list[dict]]:
    text = path.read_text(encoding="utf-8")
    epic_id = epic_id_from_path(path)
    rel_source = str(path.relative_to(REPO_ROOT))

    epic_record = {
        "epic": epic_id,
        "status": frontmatter_status(text),
        "source_file": rel_source,
    }

    headers = list(STORY_HEADER_RE.finditer(text))
    story_records = []
    for i, header in enumerate(headers):
        story_id = header.group(1)
        title = header.group(2).strip()
        block_start = header.end()
        block_end = headers[i + 1].start() if i + 1 < len(headers) else len(text)
        block = story_block(text, block_start, block_end)

        status_match = STATUS_LINE_RE.search(block)
        status = status_match.group(1).strip("*") if status_match else "not-started"

        depends_match = DEPENDS_ON_LINE_RE.search(block)
        depends_on = split_depends_on(depends_match.group(1)) if depends_match else []

        verify = [seg.strip() for seg in VERIFY_SEGMENT_RE.findall(block) if seg.strip()]

        story_records.append({
            "epic": epic_id,
            "story": story_id,
            "title": title,
            "status": status,
            "depends_on": depends_on,
            "verify": verify if verify else None,
            "source_file": rel_source,
            "capability_analysis": capability_analysis_for(block, epic_id, story_id),
        })

    return epic_record, story_records


def generate() -> dict:
    epics = []
    stories = []
    for path in sorted(EPICS_DIR.glob("EPIC-*.md")):
        epic_record, story_records = parse_epic_file(path)
        epics.append(epic_record)
        stories.extend(story_records)

    return {
        "generated_by": "tools/agent-config/generate-story-inventory.py",
        "generated_note": "Programmatically extracted from planning/epics/*.md -- no invented data.",
        "epic_count": len(epics),
        "story_count": len(stories),
        "epics": epics,
        "stories": stories,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--check", action="store_true", help="compare generated output to the repo copy")
    group.add_argument("--write", action="store_true", help="overwrite planning/story-inventory.json")
    args = parser.parse_args()

    generated = generate()
    generated_json = json.dumps(generated, indent=2, ensure_ascii=False) + "\n"

    if args.write:
        INVENTORY_PATH.write_text(generated_json, encoding="utf-8")
        print(f"wrote {INVENTORY_PATH} ({generated['epic_count']} epics, {generated['story_count']} stories)")
        return 0

    if not INVENTORY_PATH.exists():
        print(f"FAIL  {INVENTORY_PATH} does not exist")
        return 1

    current_json = INVENTORY_PATH.read_text(encoding="utf-8")
    if current_json == generated_json:
        print(f"OK    {INVENTORY_PATH} matches generator output "
              f"({generated['epic_count']} epics, {generated['story_count']} stories)")
        return 0

    current = json.loads(current_json)
    current_stories = {(s["epic"], s["story"]): s for s in current.get("stories", [])}
    generated_stories = {(s["epic"], s["story"]): s for s in generated["stories"]}

    diffs = 0
    for key in sorted(set(current_stories) | set(generated_stories)):
        cur = current_stories.get(key)
        gen = generated_stories.get(key)
        if cur != gen:
            diffs += 1
            print(f"DIFF  {key}:")
            print(f"      repo:      {json.dumps(cur, ensure_ascii=False)}")
            print(f"      generated: {json.dumps(gen, ensure_ascii=False)}")
    if current.get("epics") != generated.get("epics"):
        diffs += 1
        print("DIFF  epics[] differs")

    print(f"RESULT: FAIL ({diffs} record diff(s))")
    return 1


if __name__ == "__main__":
    sys.exit(main())
