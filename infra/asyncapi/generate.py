#!/usr/bin/env python3
"""EPIC-15 Story 15.1.

Generates infra/asyncapi/asyncapi.yaml directly from the §3.7 v2 Kafka Topic
Catalog table in sepa-nexus-message-flow-and-data-blueprint.md (ADR-N8: that
table is the sole source of truth for every topic; this script must never
hand-author a topic, only parse and re-emit what the table already says).
"""
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SOURCE_DOC = REPO_ROOT / "sepa-nexus-message-flow-and-data-blueprint.md"
OUTPUT = Path(__file__).resolve().parent / "asyncapi.yaml"

SECTION_HEADING = "### 3.7 Kafka Topic Catalog v2 / AsyncAPI Source of Truth"
TABLE_HEADER = "| Topic | Producer Owner | Key | Consumers | Ordering Guarantee | DLQ Rule | Contract Owner | MVP/P1/P2 |"


def strip_markdown(cell: str) -> str:
    """Remove backtick code-spans and bracket annotations (e.g. [ADD], [RENAME from x]),
    keeping only the plain text/data content of a table cell."""
    text = cell.strip()
    text = re.sub(r"\[[^\[\]]*\]", "", text)  # drop [ADD], [RENAME from ...], [MVP] etc.
    text = text.replace("`", "").replace("**", "")
    return re.sub(r"\s+", " ", text).strip()


def parse_topics(doc_text: str) -> list[dict]:
    section_start = doc_text.index(SECTION_HEADING)
    table_start = doc_text.index(TABLE_HEADER, section_start)
    remainder = doc_text[table_start:]
    lines = remainder.splitlines()

    topics = []
    for line in lines[2:]:  # skip header row + separator row
        if not line.startswith("|"):
            break
        raw_cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(raw_cells) != 8:
            break
        topic_name = strip_markdown(raw_cells[0])
        producer = strip_markdown(raw_cells[1])
        key = strip_markdown(raw_cells[2])
        consumers = strip_markdown(raw_cells[3])
        ordering = strip_markdown(raw_cells[4])
        dlq = strip_markdown(raw_cells[5])
        contract_owner = strip_markdown(raw_cells[6])
        priority_match = re.search(r"\[(MVP|P1|P2)\]", raw_cells[7])
        priority = priority_match.group(1) if priority_match else strip_markdown(raw_cells[7])
        topics.append({
            "name": topic_name,
            "producer_owner": producer,
            "key": key,
            "consumers": [c.strip() for c in consumers.split(",")],
            "ordering_guarantee": ordering,
            "dlq_rule": dlq,
            "contract_owner": contract_owner,
            "priority": priority,
        })
    return topics


def render_yaml(topics: list[dict]) -> str:
    lines = [
        "# generated from §3.7 v2 (sepa-nexus-message-flow-and-data-blueprint.md) — do not hand-edit.",
        "# Regenerate with: python3 infra/asyncapi/generate.py",
        "asyncapi: 3.0.0",
        "info:",
        "  title: SEPA Nexus — Kafka Topic Catalog v2",
        "  version: '1.0.0'",
        "  description: >-",
        "    Generated from §3.7 v2 (ADR-N8), the sole source of truth for every Kafka",
        "    topic in SEPA Nexus. Internal backbone only — never exposed externally.",
        "channels:",
    ]
    for topic in topics:
        channel_id = topic["name"].replace(".", "_")
        lines.append(f"  {channel_id}:")
        lines.append(f"    address: {topic['name']}")
        lines.append("    messages:")
        lines.append(f"      {channel_id}Event:")
        lines.append("        payload: {}")
        lines.append(f"    x-producer-owner: {topic['producer_owner']}")
        lines.append(f"    x-key: {topic['key']}")
        lines.append("    x-consumers:")
        for consumer in topic["consumers"]:
            lines.append(f'      - "{consumer}"')
        lines.append(f"    x-ordering-guarantee: \"{topic['ordering_guarantee']}\"")
        lines.append(f"    x-dlq-rule: \"{topic['dlq_rule']}\"")
        lines.append(f"    x-contract-owner: {topic['contract_owner']}")
        lines.append(f"    x-priority: {topic['priority']}")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    doc_text = SOURCE_DOC.read_text(encoding="utf-8")
    topics = parse_topics(doc_text)
    if not topics:
        print("No topics parsed from §3.7 v2 — refusing to write an empty catalog.", file=sys.stderr)
        return 1
    OUTPUT.write_text(render_yaml(topics), encoding="utf-8")
    print(f"Generated {OUTPUT} with {len(topics)} topics from §3.7 v2.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
