#!/usr/bin/env python3
"""Tests for markdown_h1_check pilot CLI."""
from __future__ import annotations

import importlib.util
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
MODULE_PATH = REPO / "tools" / "spec-kit-pilot" / "markdown_h1_check.py"
CLI = [sys.executable, str(MODULE_PATH)]


def load_module():
    spec = importlib.util.spec_from_file_location("markdown_h1_check", MODULE_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load {MODULE_PATH}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class CountAtxH1Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.mod = load_module()

    def test_empty_file(self) -> None:
        self.assertEqual(self.mod.count_atx_h1(""), 0)

    def test_single_h1(self) -> None:
        text = "# Title\n\nBody\n"
        self.assertEqual(self.mod.count_atx_h1(text), 1)

    def test_zero_h1(self) -> None:
        text = "## Section\n\nNo top-level heading.\n"
        self.assertEqual(self.mod.count_atx_h1(text), 0)

    def test_multiple_h1(self) -> None:
        text = "# One\n\n# Two\n"
        self.assertEqual(self.mod.count_atx_h1(text), 2)

    def test_hash_without_space_not_h1(self) -> None:
        text = "#No space\n# Real\n"
        self.assertEqual(self.mod.count_atx_h1(text), 1)

    def test_ignores_h1_inside_backtick_fence(self) -> None:
        text = "# Title\n\n```\n# not counted\n```\n"
        self.assertEqual(self.mod.count_atx_h1(text), 1)

    def test_ignores_h1_inside_tilde_fence(self) -> None:
        text = "# Title\n\n~~~\n# not counted\n~~~\n"
        self.assertEqual(self.mod.count_atx_h1(text), 1)

    def test_fence_with_language_tag(self) -> None:
        text = "# Title\n\n```python\n# ignored\n```\n"
        self.assertEqual(self.mod.count_atx_h1(text), 1)

    def test_deeper_headings_not_h1(self) -> None:
        text = "# Title\n\n## Sub\n### Subsub\n"
        self.assertEqual(self.mod.count_atx_h1(text), 1)


class AnalyzeFileTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.mod = load_module()

    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="debina-h1-check-"))

    def tearDown(self) -> None:
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_pass_single_h1(self) -> None:
        path = self.tmp / "ok.md"
        path.write_text("# Title\n", encoding="utf-8")
        result = self.mod.analyze_file(str(path))
        self.assertEqual(result.status, "PASS")
        self.assertEqual(result.h1_count, 1)

    def test_fail_zero_h1(self) -> None:
        path = self.tmp / "empty.md"
        path.write_text("", encoding="utf-8")
        result = self.mod.analyze_file(str(path))
        self.assertEqual(result.status, "FAIL")
        self.assertEqual(result.h1_count, 0)

    def test_error_missing_file(self) -> None:
        result = self.mod.analyze_file(str(self.tmp / "missing.md"))
        self.assertEqual(result.status, "ERROR")
        self.assertIsNotNone(result.reason)

    def test_error_directory(self) -> None:
        result = self.mod.analyze_file(str(self.tmp))
        self.assertEqual(result.status, "ERROR")
        self.assertIsNotNone(result.reason)

    def test_error_invalid_utf8(self) -> None:
        path = self.tmp / "bad.md"
        path.write_bytes(b"\xff\xfe")
        result = self.mod.analyze_file(str(path))
        self.assertEqual(result.status, "ERROR")
        self.assertIsNotNone(result.reason)


class CliTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="debina-h1-cli-"))

    def tearDown(self) -> None:
        shutil.rmtree(self.tmp, ignore_errors=True)

    def run_cli(self, *paths: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [*CLI, *paths],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_no_arguments_usage_exit_2(self) -> None:
        proc = self.run_cli()
        self.assertEqual(proc.returncode, 2)
        self.assertIn("usage:", proc.stderr.lower())
        self.assertIn("markdown_h1_check.py", proc.stderr)

    def test_pass_exit_0(self) -> None:
        path = self.tmp / "good.md"
        path.write_text("# Title\n", encoding="utf-8")
        proc = self.run_cli(str(path))
        self.assertEqual(proc.returncode, 0)
        self.assertEqual(proc.stdout.strip(), f"PASS {path} H1=1")

    def test_fail_exit_1(self) -> None:
        path = self.tmp / "bad.md"
        path.write_text("## Only H2\n", encoding="utf-8")
        proc = self.run_cli(str(path))
        self.assertEqual(proc.returncode, 1)
        self.assertEqual(proc.stdout.strip(), f"FAIL {path} H1=0")

    def test_error_exit_2(self) -> None:
        proc = self.run_cli(str(self.tmp / "missing.md"))
        self.assertEqual(proc.returncode, 2)
        self.assertTrue(proc.stdout.startswith("ERROR "))

    def test_mixed_fail_and_error_exit_2(self) -> None:
        good = self.tmp / "good.md"
        good.write_text("# Title\n", encoding="utf-8")
        bad = self.tmp / "bad.md"
        bad.write_text("no h1\n", encoding="utf-8")
        proc = self.run_cli(str(good), str(bad), str(self.tmp / "missing.md"))
        self.assertEqual(proc.returncode, 2)
        lines = proc.stdout.strip().splitlines()
        self.assertEqual(len(lines), 3)
        self.assertEqual(lines[0], f"PASS {good} H1=1")
        self.assertEqual(lines[1], f"FAIL {bad} H1=0")
        self.assertTrue(lines[2].startswith("ERROR "))

    def test_output_order_matches_arguments(self) -> None:
        first = self.tmp / "a.md"
        second = self.tmp / "b.md"
        first.write_text("# A\n", encoding="utf-8")
        second.write_text("# B\n", encoding="utf-8")
        proc = self.run_cli(str(second), str(first))
        self.assertEqual(proc.returncode, 0)
        lines = proc.stdout.strip().splitlines()
        self.assertEqual(lines[0], f"PASS {second} H1=1")
        self.assertEqual(lines[1], f"PASS {first} H1=1")


if __name__ == "__main__":
    unittest.main()
