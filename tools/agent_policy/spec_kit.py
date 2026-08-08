"""Spec Kit feature binding, path validation, and promotion helpers."""

from __future__ import annotations

import hashlib
import json
import re
import shutil
import tempfile
import fnmatch
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING, Any, Optional

if TYPE_CHECKING:
    from .active_state import ActiveState

CANONICAL_FEATURE_STATE = ".specify/feature.json"
MIRROR_POINTER_NAME = ".specify-feature.json"
FEATURE_DIR_RE = re.compile(r"^specs/\d{3}-[a-z0-9][a-z0-9-]*$")
FEATURE_ID_RE = re.compile(r"^\d{3}-[a-z0-9][a-z0-9-]*$")
SLUG_RE = re.compile(r"^[a-z0-9][a-z0-9-]*$")

PROTECTED = (
    ".specify/memory/",
    ".specify/templates/",
    ".specify/integration.json",
    ".specify/integrations/",
    ".specify/workflows/",
    ".specify/presets/",
    ".specify/extensions.yml",
    ".cursor/skills/",
    ".cursor/hooks/",
    ".cursor/rules/",
    ".cursor/cli.json",
    ".cursor/mcp.json",
    ".claude/skills/",
    ".agents/skills",
)

IMPL = (
    "backend/",
    "frontend/",
    "infra/",
    "dagger/",
    "tools/mcp/",
    "tools/verification/",
    "tools/spec-kit-pilot/",
    "tests/tools/spec-kit-pilot/",
)


@dataclass(frozen=True)
class SpecKitBinding:
    task_id: str
    feature_number: str
    feature_slug: str
    feature_directory: str
    feature_state_path: str = CANONICAL_FEATURE_STATE
    mirror_feature_directory: Optional[str] = None
    canonical_target: Optional[str] = None

    @property
    def feature_id(self) -> str:
        return f"{self.feature_number}-{self.feature_slug}"

    @property
    def canonical_spec_prefix(self) -> str:
        return self.feature_directory.rstrip("/")


def path_matches(path: str, patterns: list[str]) -> bool:
    for pat in patterns:
        if pat.endswith("/**"):
            prefix = pat[:-3].rstrip("/")
            if path == prefix or path.startswith(prefix + "/"):
                return True
        elif "*" in pat or "?" in pat:
            if fnmatch.fnmatch(path, pat):
                return True
        elif path == pat or path.startswith(pat.rstrip("/") + "/"):
            return True
    return False


def normalize_repo_path(path: str) -> str | None:
    if not path:
        return None
    normalized = path.replace("\\", "/")
    if normalized.startswith("./"):
        normalized = normalized[2:]
    if not normalized or normalized.startswith("/"):
        return None
    if len(normalized) > 1 and normalized[1] == ":":
        return None
    parts = normalized.split("/")
    if any(p == ".." for p in parts):
        return None
    if any(p == "" for p in parts):
        return None
    return "/".join(p for p in parts if p)


def _safe_rel(path: str) -> bool:
    return normalize_repo_path(path) is not None


def validate_feature_directory(path: str) -> tuple[bool, str]:
    norm = normalize_repo_path(path)
    if not norm:
        return False, "invalid or unsafe feature directory path"
    if not norm.startswith("specs/"):
        return False, "feature directory must be under specs/"
    if not FEATURE_DIR_RE.match(norm):
        return False, f"invalid feature directory format: {norm}"
    return True, "ok"


def validate_feature_id(feature_id: str) -> tuple[bool, str]:
    fid = feature_id.strip()
    if not FEATURE_ID_RE.match(fid):
        return False, f"invalid feature_id: {fid}"
    return True, "ok"


def validate_feature_slug(slug: str) -> tuple[bool, str]:
    s = slug.strip()
    if not SLUG_RE.match(s):
        return False, f"invalid feature_slug: {s}"
    return True, "ok"


def feature_id_matches_directory(feature_id: str, feature_directory: str) -> bool:
    norm = normalize_repo_path(feature_directory)
    if not norm:
        return False
    base = norm.rsplit("/", 1)[-1]
    return base == feature_id.strip()


def slug_matches_directory(slug: str, feature_directory: str) -> bool:
    norm = normalize_repo_path(feature_directory)
    if not norm:
        return False
    base = norm.rsplit("/", 1)[-1]
    if "-" not in base:
        return False
    return base.split("-", 1)[1] == slug.strip()


def validate_spec_kit_fields(
    *,
    feature_id: str,
    feature_slug: str,
    feature_directory: str | None,
) -> tuple[bool, str]:
    ok, reason = validate_feature_id(feature_id)
    if not ok:
        return False, reason
    ok, reason = validate_feature_slug(feature_slug)
    if not ok:
        return False, reason
    if feature_directory:
        ok, reason = validate_feature_directory(feature_directory)
        if not ok:
            return False, reason
        if not feature_id_matches_directory(feature_id, feature_directory):
            return False, "feature_id does not match feature_directory"
        if not slug_matches_directory(feature_slug, feature_directory):
            return False, "feature_slug does not match feature_directory"
    return True, "ok"


def file_sha256(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def mirror_pointer_path(repo: Path, task_id: str) -> Path:
    return repo / "work" / "active" / task_id / MIRROR_POINTER_NAME


def load_json(path: Path) -> dict[str, Any] | None:
    if not path.is_file():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None
    return data if isinstance(data, dict) else None


def parse_binding_from_mirror(data: dict[str, Any], task_id: str) -> SpecKitBinding | None:
    mirror_dir = normalize_repo_path(str(data.get("feature_directory") or ""))
    canonical = normalize_repo_path(
        str(data.get("canonical_target") or data.get("feature_directory") or "")
    )
    debina_task = str(data.get("debina_task_id") or task_id).strip()
    if not mirror_dir or not canonical or debina_task != task_id:
        return None
    slug_name = canonical.rsplit("/", 1)[-1]
    number = slug_name.split("-", 1)[0]
    if not number.isdigit():
        return None
    feature_slug = slug_name.split("-", 1)[1] if "-" in slug_name else slug_name
    return SpecKitBinding(
        task_id,
        number,
        feature_slug,
        canonical,
        CANONICAL_FEATURE_STATE,
        mirror_dir,
        canonical,
    )


def parse_binding_from_active_spec_kit(data: dict[str, Any], task_id: str) -> SpecKitBinding | None:
    sk = data.get("spec_kit")
    if not isinstance(sk, dict):
        return None
    feature_dir = normalize_repo_path(str(sk.get("feature_directory") or ""))
    feature_slug = str(sk.get("feature_slug") or "").strip()
    feature_id = str(sk.get("feature_id") or "").strip()
    if feature_dir:
        if not feature_id:
            feature_id = feature_dir.rsplit("/", 1)[-1]
        if not feature_slug and "-" in feature_id:
            feature_slug = feature_id.split("-", 1)[1]
        number = feature_id.split("-", 1)[0] if feature_id else ""
        return SpecKitBinding(
            task_id,
            number,
            feature_slug,
            feature_dir,
            str(sk.get("feature_state_path") or CANONICAL_FEATURE_STATE),
            sk.get("mirror_feature_directory"),
            feature_dir,
        )
    if feature_slug:
        number = str(sk.get("feature_number") or "")
        return SpecKitBinding(
            task_id,
            number,
            feature_slug,
            "",
            str(sk.get("feature_state_path") or CANONICAL_FEATURE_STATE),
            None,
            None,
        )
    return None


def parse_binding_from_canonical_feature(data: dict[str, Any], task_id: str) -> SpecKitBinding | None:
    feature_dir = normalize_repo_path(str(data.get("feature_directory") or ""))
    debina_task = str(data.get("debina_task_id") or "").strip()
    if not feature_dir or debina_task != task_id:
        return None
    slug_name = feature_dir.rsplit("/", 1)[-1]
    number = str(data.get("feature_number") or slug_name.split("-", 1)[0])
    short_name = str(data.get("short_name") or data.get("feature_slug") or "")
    if not short_name and "-" in slug_name:
        short_name = slug_name.split("-", 1)[1]
    return SpecKitBinding(task_id, number, short_name, feature_dir)


def resolve_binding(state: "ActiveState", repo: Path) -> SpecKitBinding | None:
    task_id = state.task_id
    if not task_id:
        return None
    b = parse_binding_from_active_spec_kit(state.raw, task_id)
    if b and (b.feature_directory or b.feature_slug):
        return b
    m = load_json(mirror_pointer_path(repo, task_id))
    if m:
        b = parse_binding_from_mirror(m, task_id)
        if b:
            return b
    c = load_json(repo / CANONICAL_FEATURE_STATE)
    if c:
        return parse_binding_from_canonical_feature(c, task_id)
    return None


def is_protected_spec_kit_path(path: str) -> bool:
    n = normalize_repo_path(path) or path.replace("\\", "/").lstrip("./")
    if n == CANONICAL_FEATURE_STATE:
        return False
    return any(n == p.rstrip("/") or n.startswith(p) for p in PROTECTED)


def is_implementation_source_path(path: str) -> bool:
    n = normalize_repo_path(path) or path.replace("\\", "/").lstrip("./")
    return any(n == p.rstrip("/") or n.startswith(p) for p in IMPL)


def is_bound_spec_path(path: str, binding: SpecKitBinding) -> bool:
    n = normalize_repo_path(path)
    if not n:
        return False
    if binding.feature_directory:
        prefix = binding.canonical_spec_prefix
        return n == prefix or n.startswith(prefix + "/")
    if binding.feature_slug:
        parts = n.split("/")
        if len(parts) < 2 or parts[0] != "specs":
            return False
        name = parts[1]
        return bool(re.match(rf"^\d{{3}}-{re.escape(binding.feature_slug)}$", name)) and (
            n == f"specs/{name}" or n.startswith(f"specs/{name}/")
        )
    return False


def is_other_spec_path(path: str, binding: SpecKitBinding | None) -> bool:
    n = normalize_repo_path(path) or path
    if not n.startswith("specs/"):
        return False
    if binding is None:
        return True
    return not is_bound_spec_path(n, binding)


def validate_feature_json_payload(
    data: dict[str, Any], state: "ActiveState", binding: SpecKitBinding
) -> tuple[bool, str]:
    feature_dir = normalize_repo_path(str(data.get("feature_directory") or ""))
    debina_task = str(data.get("debina_task_id") or "").strip()
    if debina_task != state.task_id:
        return False, "feature.json debina_task_id mismatch"
    if not feature_dir:
        return False, "missing feature_directory in feature.json"
    ok, reason = validate_feature_directory(feature_dir)
    if not ok:
        return False, reason
    if binding.feature_directory and feature_dir != binding.feature_directory:
        return False, "feature.json feature_directory mismatch"
    return True, "ok"


def canonical_feature_json_for_binding(binding: SpecKitBinding) -> dict[str, str]:
    return {
        "feature_directory": binding.feature_directory,
        "debina_task_id": binding.task_id,
        "short_name": binding.feature_slug,
        "feature_number": binding.feature_number,
        "feature_id": binding.feature_id,
    }


def collect_tree_hashes(root: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    if not root.is_dir():
        return out
    for path in sorted(root.rglob("*")):
        if path.is_file():
            out[path.relative_to(root).as_posix()] = file_sha256(path)
    return out


def check_number_collision(repo: Path, binding: SpecKitBinding) -> tuple[bool, str]:
    specs = repo / "specs"
    if not specs.is_dir():
        return True, "ok"
    number = binding.feature_number
    for child in specs.iterdir():
        if not child.is_dir():
            continue
        name = child.name
        if name.startswith(f"{number}-") and name != binding.feature_directory.rsplit("/", 1)[-1]:
            return False, "SPEC_KIT_FEATURE_NUMBER_COLLISION"
    return True, "ok"


def promote_mirror_to_canonical(repo: Path, task_id: str, *, dry_run: bool = False) -> tuple[bool, str]:
    mirror_meta = load_json(mirror_pointer_path(repo, task_id))
    if not mirror_meta:
        return False, "missing mirror .specify-feature.json"
    binding = parse_binding_from_mirror(mirror_meta, task_id)
    if not binding or not binding.mirror_feature_directory:
        return False, "invalid mirror binding"
    mirror_root = repo / binding.mirror_feature_directory
    if not mirror_root.is_dir():
        return False, "mirror feature directory missing"
    canonical_root = repo / binding.canonical_spec_prefix
    ok, reason = check_number_collision(repo, binding)
    if not ok:
        return False, reason
    mirror_hashes = collect_tree_hashes(mirror_root)
    if canonical_root.exists():
        existing = collect_tree_hashes(canonical_root)
        if existing and existing != mirror_hashes:
            return False, "SPEC_KIT_FEATURE_CONTENT_CONFLICT"
        if existing == mirror_hashes:
            payload = canonical_feature_json_for_binding(binding)
            fj = repo / CANONICAL_FEATURE_STATE
            if fj.is_file():
                cur = load_json(fj) or {}
                if cur.get("feature_directory") != payload["feature_directory"]:
                    return False, "SPEC_KIT_FEATURE_CONTENT_CONFLICT"
            elif not dry_run:
                fj.parent.mkdir(parents=True, exist_ok=True)
                fj.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
            return True, "idempotent"
    if dry_run:
        return True, "dry-run ok"
    tmp_parent = Path(tempfile.mkdtemp(prefix="debina-spec-promote-", dir=str(repo / "work")))
    staged = tmp_parent / "feature"
    try:
        shutil.copytree(mirror_root, staged)
        staged_hashes = collect_tree_hashes(staged)
        if staged_hashes != mirror_hashes:
            return False, "PROMOTION_STAGING_HASH_MISMATCH"
        canonical_root.parent.mkdir(parents=True, exist_ok=True)
        if canonical_root.exists():
            shutil.rmtree(canonical_root)
        shutil.move(str(staged), str(canonical_root))
        if collect_tree_hashes(canonical_root) != mirror_hashes:
            shutil.rmtree(canonical_root, ignore_errors=True)
            return False, "PROMOTION_VERIFY_FAILED"
        fj = repo / CANONICAL_FEATURE_STATE
        fj.parent.mkdir(parents=True, exist_ok=True)
        fj.write_text(json.dumps(canonical_feature_json_for_binding(binding), indent=2) + "\n", encoding="utf-8")
    except Exception as exc:
        if canonical_root.exists() and not collect_tree_hashes(canonical_root):
            shutil.rmtree(canonical_root, ignore_errors=True)
        return False, f"PROMOTION_FAILED: {exc}"
    finally:
        shutil.rmtree(tmp_parent, ignore_errors=True)
    return True, "promoted"


def evaluate_specification_path(
    path: str,
    state: "ActiveState",
    repo: Path,
    contents: str | None = None,
) -> tuple[str, str]:
    n = normalize_repo_path(path)
    if not n:
        n = path.replace("\\", "/")
        if n.startswith("./"):
            n = n[2:]
    binding = resolve_binding(state, repo)
    if binding is None:
        if n.startswith("specs/") or n == CANONICAL_FEATURE_STATE:
            return "deny", "SPECIFICATION scope requires bound active feature"
        if path_matches(n, [state.task_work_glob, f"work/active/{state.task_id}"]):
            return "allow", "task artifact without feature binding"
        return "deny", f"SPECIFICATION blocked (no binding): {n}"
    if is_protected_spec_kit_path(n) or is_implementation_source_path(n):
        return "deny", f"protected path in SPECIFICATION scope: {n}"
    if n == CANONICAL_FEATURE_STATE:
        if contents:
            try:
                data = json.loads(contents)
            except json.JSONDecodeError:
                return "deny", "feature.json is not valid JSON"
            if not isinstance(data, dict):
                return "deny", "feature.json must be object"
            ok, reason = validate_feature_json_payload(data, state, binding)
            if not ok:
                return "deny", reason
        return "allow", "canonical feature state"
    if is_bound_spec_path(n, binding):
        return "allow", "bound active feature spec path"
    if is_other_spec_path(n, binding):
        return "deny", "foreign Spec Kit feature path blocked"
    if path_matches(n, [state.task_work_glob, f"work/active/{state.task_id}"]):
        return "allow", "task artifact"
    return "deny", f"SPECIFICATION scope blocked: {n}"


def validate_active_spec_kit_mutation(
    current_raw: dict[str, Any], new_raw: dict[str, Any]
) -> tuple[bool, str]:
    cur_phase = str(current_raw.get("phase") or "").upper()
    cur_sk = current_raw.get("spec_kit") if isinstance(current_raw.get("spec_kit"), dict) else {}
    new_sk = new_raw.get("spec_kit") if isinstance(new_raw.get("spec_kit"), dict) else {}
    if cur_phase == "IMPLEMENTING" and cur_sk and new_sk:
        for key in ("feature_id", "feature_slug", "feature_directory", "feature_number"):
            if cur_sk.get(key) != new_sk.get(key) and new_sk.get(key) is not None:
                return False, f"cannot change spec_kit.{key} during IMPLEMENTING"
    if new_sk:
        ok, reason = validate_spec_kit_fields(
            feature_id=str(new_sk.get("feature_id") or ""),
            feature_slug=str(new_sk.get("feature_slug") or ""),
            feature_directory=new_sk.get("feature_directory"),
        )
        if not ok and new_sk.get("feature_directory"):
            return False, reason
        if new_sk.get("feature_slug") and not new_sk.get("feature_directory"):
            ok2, reason2 = validate_feature_slug(str(new_sk.get("feature_slug")))
            if not ok2:
                return False, reason2
    return True, "ok"
