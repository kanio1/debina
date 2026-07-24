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
            "verify-per-use-without-resolved-evidence.json": "ESR-015",
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
