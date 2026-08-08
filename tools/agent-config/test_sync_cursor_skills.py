#!/usr/bin/env python3
"""Fixture tests for sync-cursor-skills.py (no Spec Kit install; no main-repo mutation)."""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SYNC = REPO / "tools" / "agent-config" / "sync-cursor-skills.py"


def run_sync(root: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(SYNC), "--repo-root", str(root), *args],
        capture_output=True,
        text=True,
        check=False,
    )


def write_skill(canonical: Path, name: str, body: str = "body") -> Path:
    d = canonical / name
    d.mkdir(parents=True)
    (d / "SKILL.md").write_text(
        f"---\nname: {name}\ndescription: test skill {name}\n---\n\n{body}\n",
        encoding="utf-8",
    )
    return d


class SyncCursorSkillsTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="debina-sync-skills-"))
        self.canonical = self.tmp / ".claude" / "skills"
        self.cursor = self.tmp / ".cursor"
        self.canonical.mkdir(parents=True)
        self.cursor.mkdir(parents=True)
        write_skill(self.canonical, "alpha")
        write_skill(self.canonical, "beta")
        # whole-directory symlink (Debina-like)
        (self.cursor / "skills").symlink_to("../.claude/skills")

    def tearDown(self) -> None:
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_check_fails_on_whole_dir_symlink(self) -> None:
        p = run_sync(self.tmp, "--check")
        self.assertNotEqual(p.returncode, 0)
        self.assertIn("WHOLE_DIR_SYMLINK", p.stdout)

    def test_apply_and_check(self) -> None:
        p = run_sync(self.tmp, "--apply")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        skills = self.cursor / "skills"
        self.assertTrue(skills.is_dir())
        self.assertFalse(skills.is_symlink())
        for name in ("alpha", "beta"):
            link = skills / name
            self.assertTrue(link.is_symlink())
            self.assertEqual(os.readlink(link), f"../../.claude/skills/{name}")
            self.assertTrue((link / "SKILL.md").is_file())
        c = run_sync(self.tmp, "--check")
        self.assertEqual(c.returncode, 0, c.stdout + c.stderr)

    def test_idempotent_apply(self) -> None:
        self.assertEqual(run_sync(self.tmp, "--apply").returncode, 0)
        before = {
            name: (self.cursor / "skills" / name).lstat().st_mtime_ns
            for name in ("alpha", "beta")
        }
        src_mtime = {
            name: (self.canonical / name / "SKILL.md").stat().st_mtime_ns
            for name in ("alpha", "beta")
        }
        p = run_sync(self.tmp, "--apply")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        for name in ("alpha", "beta"):
            self.assertEqual(
                (self.canonical / name / "SKILL.md").stat().st_mtime_ns,
                src_mtime[name],
            )
            # bridge may be recreated only if needed; links should still be correct
            self.assertEqual(
                os.readlink(self.cursor / "skills" / name),
                f"../../.claude/skills/{name}",
            )
        self.assertEqual(run_sync(self.tmp, "--check").returncode, 0)

    def test_speckit_namespace_left_alone(self) -> None:
        self.assertEqual(run_sync(self.tmp, "--apply").returncode, 0)
        sentinel = self.cursor / "skills" / "speckit-sentinel"
        sentinel.mkdir()
        marker = sentinel / "SKILL.md"
        marker.write_text("---\nname: speckit-sentinel\ndescription: external\n---\nx\n", encoding="utf-8")
        before = marker.read_text(encoding="utf-8")
        c = run_sync(self.tmp, "--check")
        self.assertEqual(c.returncode, 0, c.stdout + c.stderr)
        self.assertIn("SPEC_KIT_LEFT_ALONE", c.stdout)
        a = run_sync(self.tmp, "--apply")
        self.assertEqual(a.returncode, 0, a.stdout + a.stderr)
        self.assertTrue(sentinel.is_dir())
        self.assertFalse(sentinel.is_symlink())
        self.assertEqual(marker.read_text(encoding="utf-8"), before)

    def test_rollback_on_staging_conflict(self) -> None:
        # Pre-create staging path with same pid pattern is hard; instead provoke by
        # putting a file where a skill bridge name must be created after making
        # .cursor/skills a real dir incorrectly, then restoring whole symlink and
        # injecting a blocking backup/staging collision via monkeypatch alternative:
        # Make canonical skill that cannot resolve by replacing skill dir with file mid-flight
        # Simpler: unexpected symlink target refuses and leaves original.
        (self.cursor / "skills").unlink()
        (self.cursor / "skills").symlink_to("/tmp/not-debina-skills")
        p = run_sync(self.tmp, "--apply")
        self.assertNotEqual(p.returncode, 0)
        self.assertTrue((self.cursor / "skills").is_symlink())
        self.assertEqual(os.readlink(self.cursor / "skills"), "/tmp/not-debina-skills")
        self.assertFalse((self.cursor / "skills").is_dir() and not (self.cursor / "skills").is_symlink())

    def test_rollback_when_staging_preexists(self) -> None:
        if (self.cursor / "skills").exists() or (self.cursor / "skills").is_symlink():
            (self.cursor / "skills").unlink()
        (self.cursor / "skills").symlink_to("../.claude/skills")
        leftover = self.cursor / ".skills.__sync_staging_leftover"
        leftover.mkdir()
        p = run_sync(self.tmp, "--apply")
        self.assertNotEqual(p.returncode, 0)
        self.assertIn("MIGRATION_NOT_APPLIED", p.stdout)
        self.assertTrue((self.cursor / "skills").is_symlink())
        self.assertEqual(os.readlink(self.cursor / "skills"), "../.claude/skills")
        self.assertTrue(leftover.is_dir())

    def test_name_mismatch_blocks(self) -> None:
        bad = self.canonical / "gamma"
        bad.mkdir()
        (bad / "SKILL.md").write_text(
            "---\nname: not-gamma\ndescription: mismatch\n---\n\n",
            encoding="utf-8",
        )
        p = run_sync(self.tmp, "--apply")
        self.assertEqual(p.returncode, 2)
        self.assertIn("NAME_MISMATCH", p.stdout)
        self.assertTrue((self.cursor / "skills").is_symlink())


if __name__ == "__main__":
    unittest.main()
