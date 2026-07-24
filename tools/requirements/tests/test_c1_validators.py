import subprocess
import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]

class C1ValidatorRepositoryTests(unittest.TestCase):
    def run_validator(self, relative, *args):
        return subprocess.run(
            [sys.executable, str(ROOT / relative), *map(str, args)],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

    def test_valid_pilot_use_case_and_traces_pass(self):
        result = self.run_validator("tools/requirements/validate-use-case-traceability.py")
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("validated_use_cases=15", result.stdout)

    def test_source_traceability_passes_with_known_tag_and_registry_entry(self):
        result = self.run_validator("tools/requirements/validate-source-traceability.py")
        self.assertEqual(0, result.returncode, result.stdout)

    def test_planning_semantics_passes_pilot_while_retaining_legacy_warnings(self):
        result = self.run_validator("tools/requirements/validate-planning-semantics.py")
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("warnings=", result.stdout)

    def test_valid_enforced_story_readiness_fixture_passes(self):
        fixture = (
            ROOT
            / "tools/requirements/tests/fixtures/enforced-story-readiness/valid.json"
        )
        result = self.run_validator(
            "tools/requirements/validate-planning-semantics.py", "--fixture", fixture
        )
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("errors=0", result.stdout)

    def test_invalid_enforced_story_readiness_fixtures_fail_with_specific_codes(self):
        fixture_dir = (
            ROOT / "tools/requirements/tests/fixtures/enforced-story-readiness"
        )
        cases = {
            "missing-slice.json": "ESR-002",
            "missing-evidence.json": "ESR-008",
            "missing-owner.json": "ESR-010",
            "placeholder-verify.json": "ESR-014",
            "technical-only-use-case.json": "ESR-017",
            "stale-reference.json": "ESR-004",
            "missing-source-classification.json": "ESR-007",
            "verify-per-use-without-resolved-evidence.json": "ESR-018",
            "done-not-run.json": "ESR-016",
        }
        for name, diagnostic_code in cases.items():
            with self.subTest(fixture=name):
                result = self.run_validator(
                    "tools/requirements/validate-planning-semantics.py",
                    "--fixture",
                    fixture_dir / name,
                )
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertIn(diagnostic_code, result.stdout)

    def test_source_freshness_fixtures_are_fail_closed(self):
        fixture_dir = (
            ROOT / "tools/requirements/tests/fixtures/source-evidence-freshness"
        )
        valid = self.run_validator(
            "tools/requirements/validate-source-traceability.py",
            "--fixture",
            fixture_dir / "valid-verified-evidence.json",
        )
        self.assertEqual(0, valid.returncode, valid.stdout)
        cases = {
            "verified-with-unknown-version.json": "SRC-013",
            "verified-with-unknown-effective-date.json": "SRC-013",
            "verified-with-missing-section.json": "SRC-013",
            "invalid-semantic-review-state.json": "SRC-015",
        }
        for fixture, code in cases.items():
            with self.subTest(fixture=fixture):
                result = self.run_validator(
                    "tools/requirements/validate-source-traceability.py",
                    "--fixture",
                    fixture_dir / fixture,
                )
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertIn(code, result.stdout)
                self.assertIn("SE-FIXTURE-", result.stdout)

    def test_source_confirmed_ready_requires_human_semantic_approval(self):
        fixture = (
            ROOT
            / "tools/requirements/tests/fixtures/enforced-story-readiness"
            / "source-confirmed-without-human-approval.json"
        )
        result = self.run_validator(
            "tools/requirements/validate-planning-semantics.py", "--fixture", fixture
        )
        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertIn("ESR-023", result.stdout)

    def test_readiness_freshness_fixtures_are_fail_closed(self):
        fixture_dir = (
            ROOT / "tools/requirements/tests/fixtures/enforced-story-readiness"
        )
        valid = self.run_validator(
            "tools/requirements/validate-planning-semantics.py",
            "--fixture",
            fixture_dir / "valid-verified-evidence.json",
        )
        self.assertEqual(0, valid.returncode, valid.stdout)
        cases = {
            "ready-with-incomplete-evidence.json": "ESR-018",
            "ready-with-stale-evidence.json": "ESR-019",
            "ready-with-conflicting-evidence.json": "ESR-018",
            "ready-with-restricted-participant-evidence.json": "ESR-018",
            "project-interpretation-without-adr.json": "ESR-021",
            "project-simulation-without-boundary.json": "ESR-022",
            "superseded-evidence-used-as-current.json": "ESR-019",
        }
        for fixture, code in cases.items():
            with self.subTest(fixture=fixture):
                result = self.run_validator(
                    "tools/requirements/validate-planning-semantics.py",
                    "--fixture",
                    fixture_dir / fixture,
                )
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertIn(code, result.stdout)
                self.assertIn("SE-FIXTURE-", result.stdout)
