import subprocess
import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
FIXTURES = ROOT / "tools/planning/tests/fixtures/phase-e"
VALIDATOR = ROOT / "tools/planning/validate-phase-e-artifacts.py"


class PhaseEArtifactValidatorTests(unittest.TestCase):
    def run_fixture(self, name: str):
        return subprocess.run(
            [sys.executable, str(VALIDATOR), "--fixture", str(FIXTURES / name)],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

    def test_valid_e1_proposal_passes(self):
        result = self.run_fixture("valid-e1-proposal.yaml")
        self.assertEqual(0, result.returncode, result.stdout)

    def test_negative_contract_fixtures_fail_with_specific_diagnostic(self):
        cases = {
            "unknown-use-case.yaml": "PE-001",
            "wrong-slice-owner.yaml": "PE-002",
            "unknown-proposed-story.yaml": "PE-008",
            "source-tags-as-classification.yaml": "PE-009",
            "classification-list-instead-of-scalar.yaml": "PE-009",
            "blocked-and-ready-contradiction.yaml": "PE-012",
            "unknown-evidence.yaml": "PE-005",
            "unknown-rule.yaml": "PE-004",
            "unknown-module.yaml": "PE-006",
            "unknown-quality-scenario.yaml": "PE-007",
            "invalid-review-queue.yaml": "PE-011",
            "noncanonical-slice-syntax.yaml": "PE-013",
            "source-confirmed-ready-without-human-approval.yaml": "PE-015",
        }
        for fixture, code in cases.items():
            with self.subTest(fixture=fixture):
                result = self.run_fixture(fixture)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertIn(code, result.stdout)


if __name__ == "__main__":
    unittest.main()
