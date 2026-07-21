import subprocess
import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]

class C1ValidatorRepositoryTests(unittest.TestCase):
    def run_validator(self, relative):
        return subprocess.run([sys.executable, str(ROOT / relative)], cwd=ROOT, text=True, capture_output=True)

    def test_valid_pilot_use_case_and_traces_pass(self):
        result = self.run_validator("tools/requirements/validate-use-case-traceability.py")
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("validated_use_cases=1", result.stdout)

    def test_source_traceability_passes_with_known_tag_and_registry_entry(self):
        result = self.run_validator("tools/requirements/validate-source-traceability.py")
        self.assertEqual(0, result.returncode, result.stdout)

    def test_planning_semantics_passes_pilot_while_retaining_legacy_warnings(self):
        result = self.run_validator("tools/requirements/validate-planning-semantics.py")
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("warnings=", result.stdout)
