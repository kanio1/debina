#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import date
from pathlib import Path

import yaml

from validation_common import ROOT, diagnostic, finish, ids_from_catalog, source_ids

KNOWN = {
    "EU-LAW", "EPC-SCT", "EPC-SCT-INST", "ISO20022", "TIPS", "STEP2",
    "RT1", "STET", "PROJECT-ADR", "PROJECT_SIMULATION", "ASSUMPTION",
    "OPEN-QUESTION", "PARTICIPANT_DOCUMENTATION_REQUIRED",
}
RAIL_STATUS = {
    "APPLICABLE", "APPLICABLE-WITH-RAIL-EXTENSION", "NOT-APPLICABLE",
    "PROJECT_SIMULATION", "SOURCE_BLOCKED",
    "PARTICIPANT_DOCUMENTATION_REQUIRED",
}
EVIDENCE_STATUS = {
    "VERIFIED", "VERIFY_PER_USE", "INCOMPLETE", "STALE", "CONFLICTING",
    "RESTRICTED",
}
VERIFICATION_METHOD = {
    "HUMAN_REVIEW", "OFFICIAL_DOCUMENT_REVIEW", "PROJECT_ADR", "OTHER",
}
REQUIRED_EVIDENCE_FIELDS = (
    "id", "source_registry_id", "document_title", "publisher",
    "document_version", "publication_date", "effective_date", "section", "rail",
    "public_or_restricted", "access_date", "supported_claim",
    "project_interpretation", "applicable_rules", "confidence",
    "evidence_status", "last_verified_date", "next_review_date",
    "effective_from", "effective_until", "superseded_by",
    "verification_method", "reviewer",
)


def scalar(value: object) -> str:
    return str(value) if value is not None else ""


def parsed_date(value: object, allowed: set[str]) -> date | None:
    text = scalar(value)
    if text in allowed:
        return None
    try:
        return date.fromisoformat(text)
    except ValueError:
        return None


def validate_evidence_entries(
    entries: list[dict],
    known_sources: set[str],
    known_rules: set[str],
    location: str,
) -> int:
    errors = 0
    evidence_ids = {scalar(item.get("id")) for item in entries if item.get("id")}

    def fail(code: str, evidence_id: str, message: str) -> None:
        nonlocal errors
        errors += 1
        diagnostic("ERROR", code, location, evidence_id, "source-evidence", message)

    for item in entries:
        evidence_id = scalar(item.get("id")) or "UNKNOWN-EVIDENCE"
        for field in REQUIRED_EVIDENCE_FIELDS:
            if field not in item or item[field] in (None, "", []):
                fail("SRC-009", evidence_id, f"mandatory freshness/evidence field {field} missing")

        source = scalar(item.get("source_registry_id"))
        if source and source not in known_sources:
            fail("SRC-007", evidence_id, f"source registry reference {source} missing")
        for rule in item.get("applicable_rules", []) or []:
            if scalar(rule) not in known_rules:
                fail("SRC-008", evidence_id, f"unknown applicable rule {rule}")

        status = scalar(item.get("evidence_status"))
        if status and status not in EVIDENCE_STATUS:
            fail("SRC-010", evidence_id, f"unsupported evidence_status {status}")
        method = scalar(item.get("verification_method"))
        if method and method not in VERIFICATION_METHOD:
            fail("SRC-010", evidence_id, f"unsupported verification_method {method}")

        date_fields = {
            "last_verified_date": {"UNKNOWN"},
            "next_review_date": {"NOT_SCHEDULED"},
            "effective_from": {"UNKNOWN"},
            "effective_until": {"OPEN", "UNKNOWN"},
        }
        dates: dict[str, date | None] = {}
        for field, allowed in date_fields.items():
            value = item.get(field)
            dates[field] = parsed_date(value, allowed)
            if value is not None and scalar(value) not in allowed and dates[field] is None:
                fail("SRC-011", evidence_id, f"{field} must be YYYY-MM-DD or {sorted(allowed)}")
        if dates.get("last_verified_date") and dates.get("next_review_date"):
            if dates["next_review_date"] < dates["last_verified_date"]:
                fail("SRC-011", evidence_id, "next_review_date precedes last_verified_date")
        if dates.get("effective_from") and dates.get("effective_until"):
            if dates["effective_until"] < dates["effective_from"]:
                fail("SRC-011", evidence_id, "effective_until precedes effective_from")

        superseded = scalar(item.get("superseded_by"))
        if superseded == evidence_id:
            fail("SRC-012", evidence_id, "superseded_by cannot self-reference")
        elif superseded not in {"", "NONE", "UNKNOWN"} and superseded not in evidence_ids:
            fail("SRC-012", evidence_id, f"superseded_by {superseded} does not resolve")

        if status == "VERIFIED":
            contradictions = []
            if scalar(item.get("document_version")) == "VERIFY_PER_USE":
                contradictions.append("document_version VERIFY_PER_USE")
            if scalar(item.get("publication_date")) == "UNKNOWN":
                contradictions.append("publication_date UNKNOWN")
            if scalar(item.get("effective_date")) == "UNKNOWN":
                contradictions.append("effective_date UNKNOWN")
            if scalar(item.get("section")) == "SECTION-NOT-AVAILABLE":
                contradictions.append("section SECTION-NOT-AVAILABLE")
            if scalar(item.get("confidence")) == "LOW":
                contradictions.append("confidence LOW")
            for contradiction in contradictions:
                fail("SRC-013", evidence_id, f"VERIFIED conflicts with {contradiction}")

        restriction = scalar(item.get("public_or_restricted")).lower()
        if restriction in {"restricted", "participant-only", "participant-restricted"}:
            if status != "RESTRICTED":
                fail("SRC-014", evidence_id, "participant/restricted evidence must be RESTRICTED")
            if scalar(item.get("repository_content")) != "METADATA_ONLY":
                fail("SRC-014", evidence_id, "restricted evidence may store METADATA_ONLY")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", type=Path)
    args = parser.parse_args()

    known_sources = source_ids()
    known_rules = ids_from_catalog(
        ROOT / "docs/requirements/BUSINESS-RULE-CATALOG.yaml", "BR"
    )
    if args.fixture:
        fixture = json.loads(args.fixture.read_text(encoding="utf-8"))
        errors = validate_evidence_entries(
            fixture.get("evidence", []),
            known_sources,
            known_rules,
            str(args.fixture),
        )
        print(
            "ASSURANCE [STRUCTURAL] freshness metadata was checked; "
            "semantic truth requires source review."
        )
        return finish(errors, 0, validated_evidence=len(fixture.get("evidence", [])))

    errors = warnings = 0
    paths = [
        ROOT / "docs/requirements/BUSINESS-RULE-CATALOG.yaml",
        ROOT / "docs/requirements/USE-CASE-CATALOG.yaml",
    ]
    for path in paths:
        text = path.read_text(encoding="utf-8")
        for tag in re.findall(r"\[([A-Z0-9_-]+)\]", text):
            if tag not in KNOWN and not tag.startswith(
                ("UC-", "UCS-", "BR-", "QS-", "EPIC-")
            ):
                errors += 1
                diagnostic(
                    "ERROR", "SRC-001", path.relative_to(ROOT), tag,
                    "known-source-tag", "unknown tag",
                )
        for reference_list in re.findall(r"source_references:\s*\[([^]]*)\]", text):
            for source in (part.strip() for part in reference_list.split(",")):
                if source and source not in known_sources:
                    errors += 1
                    diagnostic(
                        "ERROR", "SRC-002", path.relative_to(ROOT), source,
                        "source-registry", "reference missing",
                    )

    rules_text = (
        ROOT / "docs/requirements/BUSINESS-RULE-CATALOG.yaml"
    ).read_text(encoding="utf-8")
    if re.search(r"id:\s*BR-", rules_text) and "source_references:" not in rules_text:
        errors += 1
        diagnostic(
            "ERROR", "SRC-003", "BUSINESS-RULE-CATALOG.yaml", "rules",
            "external-rule-source", "missing source references",
        )

    for path in ROOT.glob("docs/requirements/use-cases/*.md"):
        for values in re.findall(r"rail_applicability:\s*\[([^]]*)\]", path.read_text()):
            for value in (part.strip() for part in values.split(",")):
                if value and value not in RAIL_STATUS:
                    errors += 1
                    diagnostic(
                        "ERROR", "SRC-004", path.relative_to(ROOT), value,
                        "rail-applicability", "unsupported value",
                    )

    evidence_path = ROOT / "docs/standards/SOURCE-EVIDENCE-CATALOG.yaml"
    if not evidence_path.is_file():
        errors += 1
        diagnostic(
            "ERROR", "SRC-005", evidence_path.relative_to(ROOT), "catalog",
            "source-evidence", "missing source evidence catalogue",
        )
    else:
        data = yaml.safe_load(evidence_path.read_text(encoding="utf-8"))
        errors += validate_evidence_entries(
            data.get("evidence", []),
            known_sources,
            known_rules,
            str(evidence_path.relative_to(ROOT)),
        )
    print(
        "ASSURANCE [REFERENTIAL] source registry, evidence freshness and "
        "catalogue references were checked; semantic truth requires source review."
    )
    return finish(errors, warnings, validated_sources=len(known_sources))


if __name__ == "__main__":
    sys.exit(main())
