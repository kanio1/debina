import subprocess
import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]

class C1ArchitectureValidatorRepositoryTests(unittest.TestCase):
    def test_module_catalog_passes(self):
        result = subprocess.run([sys.executable, str(ROOT / "tools/architecture/validate-module-catalog.py")], cwd=ROOT, text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stdout)

    def test_legacy_adrs_warn_but_do_not_fail(self):
        result = subprocess.run([sys.executable, str(ROOT / "tools/architecture/validate-adr-lifecycle.py")], cwd=ROOT, text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stdout)
