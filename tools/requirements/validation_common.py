"""Small shared parsing/diagnostic helpers for C1 repository validators."""
from __future__ import annotations
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STORY_RE = re.compile(r"^## Story ([0-9]+(?:[A-Z]|\.[0-9]+[A-Z]?)*) — (.+)$", re.M)
META_RE = re.compile(r"^([a-z_]+):\s*(.+)$", re.M)

def diagnostic(level, code, path, subject, rule, text):
    print(f"{level} {code} {path}: {subject}: {rule}: {text}")

def story_blocks(root=ROOT):
    for path in sorted((root / "planning/epics").glob("EPIC-*.md")):
        text = path.read_text(encoding="utf-8")
        heads = list(STORY_RE.finditer(text))
        top = text[:heads[0].start()] if heads else text
        epic_meta = dict(META_RE.findall(top))
        for i, head in enumerate(heads):
            block = text[head.end(): heads[i + 1].start() if i + 1 < len(heads) else len(text)]
            meta = dict(META_RE.findall(block))
            yield path, path.stem.split("-", 2)[0] + "-" + path.stem.split("-", 2)[1], head.group(1), block, {**epic_meta, **meta}

def list_value(value):
    if not value:
        return []
    return [x.strip().strip("'\"") for x in value.strip().strip("[]").split(",") if x.strip()]

def ids_from_catalog(path, key):
    return set(re.findall(rf"\bid:\s*({key}-[A-Z0-9-]+)", path.read_text(encoding="utf-8")))

def source_ids(root=ROOT):
    return set(re.findall(r"^\s*- id:\s*([^\s]+)", (root / "docs/standards/SOURCE-REGISTRY.yaml").read_text(), re.M))

def finish(errors, warnings, **counts):
    print("SUMMARY " + " ".join([f"errors={errors}", f"warnings={warnings}"] + [f"{k}={v}" for k, v in counts.items()]))
    return 1 if errors else 0
