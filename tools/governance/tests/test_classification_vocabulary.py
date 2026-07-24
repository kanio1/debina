import subprocess
import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / "tools/governance/validate-classification-vocabulary.py"
FIXTURES = ROOT / "tools/governance/tests/fixtures/classification-vocabulary"


class ClassificationVocabularyTests(unittest.TestCase):
    def run_fixture(self, name):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--fixture", str(FIXTURES / name)],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

    def test_repository_vocabulary_passes(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPT)],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stdout)

    def test_valid_fixture_passes(self):
        result = self.run_fixture("valid.json")
        self.assertEqual(0, result.returncode, result.stdout)

    def test_negative_fixtures_fail_with_causal_code(self):
        cases = {
            "duplicate-canonical.json": "CV-001",
            "duplicate-alias.json": "CV-002",
            "alias-collision.json": "CV-003",
            "unknown-producer.json": "CV-004",
            "unknown-consumer.json": "CV-005",
            "source-confirmed-alias-in-skill.json": "CV-006",
            "project-interpretation-alias-in-skill.json": "CV-006",
            "unknown-machine-token.json": "CV-007",
            "deprecated-alias-active-artifact.json": "CV-008",
            "unproduced-consumed-token.json": "CV-009",
            "false-model-behavior-pass.json": "CV-010",
        }
        for fixture, code in cases.items():
            with self.subTest(fixture=fixture):
                result = self.run_fixture(fixture)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertIn(code, result.stdout)
