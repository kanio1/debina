#!/usr/bin/env python3
"""Synchronize Cursor per-Skill bridges to canonical .claude/skills.

Modes:
  --check     read-only validation (exit 0 = OK)
  --dry-run   print planned apply actions without mutating
  --apply     migrate whole-directory symlink → real dir + per-Skill bridges

Ownership:
  symlink → .claude/skills/<name>  = DEBINA_MANAGED
  real dir  .cursor/skills/speckit-* = SPEC_KIT_MANAGED (left alone)
  other real dirs                    = UNKNOWN (left alone; --check warns)

Does not edit Skill contents. Does not create or manage speckit-*.
"""
from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

SPEC_KIT_PREFIX = "speckit-"
NAME_RE = re.compile(r"^name:\s*[\"']?([^\"'\n]+)[\"']?\s*$", re.MULTILINE)


@dataclass
class EligibleSkill:
    name: str
    canonical: Path
    skill_md: Path
    sha256: str
    frontmatter_name: str


@dataclass
class Finding:
    level: str  # ERROR | WARN | INFO
    code: str
    message: str


def repo_root_from_cwd() -> Path:
    cwd = Path.cwd().resolve()
    if (cwd / ".claude" / "skills").is_dir() and (cwd / ".cursor").exists():
        return cwd
    # walk up a few levels
    for p in [cwd, *cwd.parents]:
        if (p / ".claude" / "skills").is_dir() and (p / ".git").exists():
            return p
    raise SystemExit("sync-cursor-skills: not inside a Debina-like repo root")


def parse_frontmatter_name(text: str) -> str | None:
    if not text.startswith("---"):
        return None
    end = text.find("\n---", 3)
    block = text[3:end] if end != -1 else text[3:]
    m = NAME_RE.search(block)
    return m.group(1).strip() if m else None


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def list_eligible(canonical_root: Path, findings: list[Finding]) -> list[EligibleSkill]:
    if not canonical_root.is_dir():
        findings.append(Finding("ERROR", "NO_CANONICAL", f"missing {canonical_root}"))
        return []

    out: list[EligibleSkill] = []
    seen_norm: dict[str, str] = {}
    for entry in sorted(canonical_root.iterdir(), key=lambda p: p.name.lower()):
        if not entry.is_dir() or entry.is_symlink():
            if entry.is_file():
                findings.append(
                    Finding("WARN", "NON_DIR", f"ignoring non-directory {entry}")
                )
            continue
        name = entry.name
        if name.startswith(SPEC_KIT_PREFIX):
            findings.append(
                Finding(
                    "ERROR",
                    "NAMESPACE_COLLISION",
                    f"canonical Debina skill must not use Spec Kit namespace: {name}",
                )
            )
            continue
        skill_md = entry / "SKILL.md"
        if not skill_md.is_file():
            findings.append(
                Finding("WARN", "INCOMPLETE", f"skip incomplete skill dir (no SKILL.md): {name}")
            )
            continue
        text = skill_md.read_text(encoding="utf-8", errors="replace")
        fm_name = parse_frontmatter_name(text)
        if not fm_name:
            findings.append(
                Finding("ERROR", "FRONTMATTER", f"missing/invalid frontmatter name: {name}")
            )
            continue
        if fm_name != name:
            findings.append(
                Finding(
                    "ERROR",
                    "NAME_MISMATCH",
                    f"directory {name!r} != frontmatter name {fm_name!r}",
                )
            )
            continue
        key = name.casefold()
        if key in seen_norm:
            findings.append(
                Finding(
                    "ERROR",
                    "DUPLICATE_NAME",
                    f"duplicate skill names after normalization: {seen_norm[key]} vs {name}",
                )
            )
            continue
        seen_norm[key] = name
        out.append(
            EligibleSkill(
                name=name,
                canonical=entry,
                skill_md=skill_md,
                sha256=sha256_file(skill_md),
                frontmatter_name=fm_name,
            )
        )
    return out


def expected_rel_target(skill_name: str) -> str:
    return f"../../.claude/skills/{skill_name}"


def resolve_no_cycle(path: Path, limit: int = 32) -> Path | None:
    """Follow symlinks without treating resolve()-followed targets as a false cycle."""
    seen: set[Path] = set()
    cur = path
    for _ in range(limit):
        # absolute() does not follow the final symlink; resolve() would and false-cycle.
        key = cur if cur.is_absolute() else Path.cwd() / cur
        key = Path(os.path.abspath(key))
        if key in seen:
            return None
        seen.add(key)
        if not cur.is_symlink():
            try:
                return cur.resolve()
            except OSError:
                return cur
        target = Path(os.readlink(cur))
        cur = target if target.is_absolute() else (cur.parent / target)
    return None


def classify_cursor_entry(entry: Path, canonical_root: Path) -> str:
    if entry.name.startswith(SPEC_KIT_PREFIX):
        if entry.is_symlink():
            return "SPEC_KIT_UNEXPECTED_SYMLINK"
        return "SPEC_KIT_MANAGED"
    if entry.is_symlink():
        resolved = resolve_no_cycle(entry)
        if resolved is None:
            return "CYCLE"
        try:
            canon = canonical_root.resolve()
            resolved.relative_to(canon)
            if resolved.name == entry.name and (resolved / "SKILL.md").is_file():
                return "DEBINA_MANAGED"
        except ValueError:
            return "FOREIGN_SYMLINK"
        return "FOREIGN_SYMLINK"
    if entry.is_dir():
        return "UNKNOWN"
    return "UNKNOWN_FILE"


def check_layout(root: Path) -> tuple[int, list[Finding], list[EligibleSkill]]:
    findings: list[Finding] = []
    canonical = root / ".claude" / "skills"
    cursor_skills = root / ".cursor" / "skills"
    eligible = list_eligible(canonical, findings)

    if any(f.level == "ERROR" and f.code == "NAMESPACE_COLLISION" for f in findings):
        return 2, findings, eligible

    if not cursor_skills.exists():
        findings.append(Finding("ERROR", "MISSING_CURSOR_SKILLS", f"missing {cursor_skills}"))
        return 1, findings, eligible

    if cursor_skills.is_symlink():
        findings.append(
            Finding(
                "ERROR",
                "WHOLE_DIR_SYMLINK",
                f"{cursor_skills} is a whole-directory symlink → {os.readlink(cursor_skills)}",
            )
        )
        return 1, findings, eligible

    if not cursor_skills.is_dir():
        findings.append(Finding("ERROR", "NOT_DIR", f"{cursor_skills} is not a directory"))
        return 1, findings, eligible

    # Index cursor entries
    bridges_ok = 0
    targets_seen: dict[str, str] = {}
    for entry in sorted(cursor_skills.iterdir(), key=lambda p: p.name.lower()):
        kind = classify_cursor_entry(entry, canonical)
        if kind == "SPEC_KIT_MANAGED":
            findings.append(
                Finding("INFO", "SPEC_KIT_LEFT_ALONE", f"leaving Spec Kit dir untouched: {entry.name}")
            )
            continue
        if kind == "SPEC_KIT_UNEXPECTED_SYMLINK":
            findings.append(
                Finding(
                    "ERROR",
                    "SPEC_KIT_SYMLINK",
                    f"speckit entry must be a real directory, not a symlink: {entry.name}",
                )
            )
            continue
        if kind in {"UNKNOWN", "UNKNOWN_FILE"}:
            findings.append(
                Finding(
                    "ERROR",
                    "UNKNOWN_OWNERSHIP",
                    f"unknown ownership under .cursor/skills: {entry.name} ({kind})",
                )
            )
            continue
        if kind == "CYCLE":
            findings.append(Finding("ERROR", "SYMLINK_CYCLE", f"symlink cycle at {entry}"))
            continue
        if kind == "FOREIGN_SYMLINK":
            findings.append(
                Finding("ERROR", "BAD_BRIDGE", f"bridge does not resolve under .claude/skills: {entry.name}")
            )
            continue
        # DEBINA_MANAGED
        if not entry.is_symlink():
            findings.append(Finding("ERROR", "NOT_SYMLINK", f"Debina bridge must be symlink: {entry.name}"))
            continue
        raw = os.readlink(entry)
        expected = expected_rel_target(entry.name)
        if Path(raw).as_posix() != expected:
            # accept equivalent resolved path only if same name under canonical
            resolved = resolve_no_cycle(entry)
            if (
                resolved is None
                or resolved.name != entry.name
                or resolved.parent.resolve() != canonical.resolve()
            ):
                findings.append(
                    Finding(
                        "ERROR",
                        "BAD_TARGET",
                        f"{entry.name}: link {raw!r} expected {expected!r}",
                    )
                )
                continue
        resolved = resolve_no_cycle(entry)
        if resolved is None or not resolved.exists():
            findings.append(Finding("ERROR", "BROKEN_LINK", f"broken symlink: {entry.name}"))
            continue
        if not (resolved / "SKILL.md").is_file():
            findings.append(Finding("ERROR", "NO_SKILL_MD", f"target missing SKILL.md: {entry.name}"))
            continue
        key = str(resolved.resolve())
        if key in targets_seen:
            findings.append(
                Finding(
                    "ERROR",
                    "DUPLICATE_TARGET",
                    f"duplicate bridge targets: {targets_seen[key]} and {entry.name}",
                )
            )
            continue
        targets_seen[key] = entry.name
        bridges_ok += 1

    eligible_names = {s.name for s in eligible}
    bridge_names = {
        p.name
        for p in cursor_skills.iterdir()
        if p.is_symlink() and classify_cursor_entry(p, canonical) == "DEBINA_MANAGED"
    }
    missing = sorted(eligible_names - bridge_names)
    extra = sorted(bridge_names - eligible_names)
    for name in missing:
        findings.append(Finding("ERROR", "MISSING_BRIDGE", f"missing per-Skill bridge: {name}"))
    for name in extra:
        findings.append(
            Finding("ERROR", "EXTRA_BRIDGE", f"bridge without eligible canonical skill: {name}")
        )

    if bridges_ok == len(eligible) and not missing and not extra:
        findings.append(
            Finding(
                "INFO",
                "COUNT_MATCH",
                f"eligible canonical Skills ({len(eligible)}) == valid bridges ({bridges_ok})",
            )
        )

    errors = sum(1 for f in findings if f.level == "ERROR")
    return (0 if errors == 0 else 1), findings, eligible


def build_staging(root: Path, eligible: list[EligibleSkill], staging: Path) -> None:
    staging.mkdir(parents=True, exist_ok=False)
    for skill in eligible:
        link = staging / skill.name
        link.symlink_to(expected_rel_target(skill.name))
        # verify immediately
        if not link.is_symlink():
            raise RuntimeError(f"failed to create symlink {link}")
        resolved = resolve_no_cycle(link)
        if resolved is None or not (resolved / "SKILL.md").is_file():
            raise RuntimeError(f"staging bridge broken for {skill.name}")
        if resolved.resolve() != skill.canonical.resolve():
            raise RuntimeError(
                f"staging bridge for {skill.name} resolves to {resolved}, "
                f"expected {skill.canonical}"
            )


def apply_migration(root: Path, dry_run: bool) -> int:
    findings: list[Finding] = []
    canonical = root / ".claude" / "skills"
    cursor_skills = root / ".cursor" / "skills"
    eligible = list_eligible(canonical, findings)

    for f in findings:
        print(f"{f.level} {f.code}: {f.message}")
    if any(f.level == "ERROR" for f in findings):
        print("CURSOR_SKILLS_NAMESPACE_COLLISION_OR_INVALID_CANONICAL")
        print("MIGRATION_NOT_APPLIED")
        return 2

    if not canonical.is_dir():
        print("ERROR: canonical .claude/skills missing")
        return 2

    # Already migrated → reconcile bridges only (idempotent)
    if cursor_skills.exists() and not cursor_skills.is_symlink() and cursor_skills.is_dir():
        planned: list[str] = []
        for skill in eligible:
            link = cursor_skills / skill.name
            expected = expected_rel_target(skill.name)
            if link.is_symlink() and os.readlink(link) == expected:
                continue
            if link.exists() and not link.is_symlink():
                if skill.name.startswith(SPEC_KIT_PREFIX):
                    continue
                print(f"ERROR: refusing to replace non-symlink entry {link}")
                return 2
            planned.append(f"ensure bridge {skill.name} -> {expected}")
        # Never touch speckit-* or unknown real dirs
        for entry in cursor_skills.iterdir():
            kind = classify_cursor_entry(entry, canonical)
            if kind == "SPEC_KIT_MANAGED":
                planned.append(f"leave SPEC_KIT_MANAGED {entry.name}")
            elif kind in {"UNKNOWN", "UNKNOWN_FILE"} and not any(
                s.name == entry.name for s in eligible
            ):
                print(f"ERROR UNKNOWN_OWNERSHIP: {entry.name}")
                return 2
        if dry_run:
            print("DRY-RUN (already real directory):")
            for line in planned or ["no bridge changes needed"]:
                print(f"  - {line}")
            return 0
        for skill in eligible:
            link = cursor_skills / skill.name
            expected = expected_rel_target(skill.name)
            if link.is_symlink() and os.readlink(link) == expected:
                continue
            if link.is_symlink() or link.exists():
                link.unlink()
            link.symlink_to(expected)
        code, check_findings, _ = check_layout(root)
        for f in check_findings:
            if f.level != "INFO":
                print(f"{f.level} {f.code}: {f.message}")
        print("apply: idempotent reconcile complete" if code == 0 else "apply: check failed after reconcile")
        return code

    if not cursor_skills.exists():
        print("ERROR: .cursor/skills missing")
        return 2

    if not cursor_skills.is_symlink():
        print("ERROR: .cursor/skills exists but is not a symlink and not a directory we can reconcile")
        return 2

    raw_target = os.readlink(cursor_skills)
    if raw_target != "../.claude/skills":
        print(
            f"ERROR: refusing unexpected whole-directory symlink target {raw_target!r}; "
            f"expected '../.claude/skills'"
        )
        print("MIGRATION_NOT_APPLIED")
        return 2

    print(f"eligible Skills: {len(eligible)}")
    for skill in eligible:
        print(f"  bridge {skill.name} -> {expected_rel_target(skill.name)}")

    if dry_run:
        print("DRY-RUN: would replace whole-directory symlink with real directory + per-Skill bridges")
        print("DRY-RUN: would not create speckit-*")
        print("DRY-RUN: would not modify .claude/skills contents")
        return 0

    parent = cursor_skills.parent
    staging = parent / f".skills.__sync_staging_{os.getpid()}"
    backup = parent / f".skills.__sync_backup_{os.getpid()}"
    leftovers = [
        p
        for p in parent.iterdir()
        if p.name.startswith(".skills.__sync_staging_")
        or p.name.startswith(".skills.__sync_backup_")
    ]
    if leftovers:
        print(f"ERROR: leftover staging/backup present: {[p.name for p in leftovers]}")
        print("MIGRATION_NOT_APPLIED")
        return 2

    hashes_before = {s.name: s.sha256 for s in eligible}
    try:
        build_staging(root, eligible, staging)
        # Atomic-ish swap: move symlink aside, move staging into place, remove backup symlink
        os.rename(cursor_skills, backup)
        try:
            os.rename(staging, cursor_skills)
        except Exception:
            # restore original symlink
            if cursor_skills.exists():
                if cursor_skills.is_dir() and not cursor_skills.is_symlink():
                    shutil.rmtree(cursor_skills)
                else:
                    cursor_skills.unlink(missing_ok=True)
            os.rename(backup, cursor_skills)
            raise
        backup.unlink()  # remove old whole-dir symlink only
    except Exception as exc:
        # best-effort cleanup of staging
        if staging.exists():
            shutil.rmtree(staging, ignore_errors=True)
        print(f"ERROR during apply: {exc}")
        print("MIGRATION_ROLLED_BACK" if cursor_skills.is_symlink() else "MIGRATION_PARTIAL_STATE")
        return 2

    # Verify hashes unchanged
    for skill in eligible:
        after = sha256_file(skill.skill_md)
        if after != hashes_before[skill.name]:
            print(f"ERROR: source hash changed for {skill.name}")
            return 2

    code, check_findings, _ = check_layout(root)
    for f in check_findings:
        if f.level != "INFO":
            print(f"{f.level} {f.code}: {f.message}")
    if code != 0:
        print("ERROR: post-apply --check failed")
        return 2
    print(f"apply: OK — {len(eligible)} per-Skill bridges; .claude/skills untouched")
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    mode = ap.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--apply", action="store_true")
    mode.add_argument("--dry-run", action="store_true")
    ap.add_argument(
        "--repo-root",
        type=Path,
        default=None,
        help="Repository root (default: detect from cwd). For tests/fixtures only.",
    )
    args = ap.parse_args(argv)

    root = args.repo_root.resolve() if args.repo_root else repo_root_from_cwd()
    if not (root / ".claude" / "skills").exists():
        print(f"ERROR: {root}/.claude/skills missing")
        return 2

    if args.check:
        code, findings, eligible = check_layout(root)
        for f in findings:
            print(f"{f.level} {f.code}: {f.message}")
        print(f"eligible={len(eligible)} errors={sum(1 for f in findings if f.level == 'ERROR')}")
        return code

    if args.dry_run:
        return apply_migration(root, dry_run=True)

    return apply_migration(root, dry_run=False)


if __name__ == "__main__":
    raise SystemExit(main())
