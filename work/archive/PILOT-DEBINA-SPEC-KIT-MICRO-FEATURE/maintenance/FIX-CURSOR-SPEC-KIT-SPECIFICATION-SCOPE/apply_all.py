from pathlib import Path
import py_compile
import shutil

REPO = Path("/home/suso/debina")
ROOT = Path(__file__).resolve().parent
SRC = ROOT / "sources"

MAP = {
    "spec_kit.py": "tools/agent_policy/spec_kit.py",
    "active_state.py": "tools/agent_policy/active_state.py",
    "write_gate.py": "tools/agent_policy/write_gate.py",
    "set_task_state.py": "tools/agent/set_task_state.py",
    "promote_spec_kit_feature.py": "tools/agent/promote_spec_kit_feature.py",
    "promote-spec-kit-feature": "tools/agent/promote-spec-kit-feature",
    "health-check.py": ".cursor/hooks/health-check.py",
}


def patch_shell_policy():
    path = REPO / "tools/agent_policy/shell_policy.py"
    text = path.read_text(encoding="utf-8")
    needle = '"bash tools/agent/",'
    insert = '"bash tools/agent/promote-spec-kit-feature",\n        ' + needle
    if "promote-spec-kit-feature" not in text:
        text = text.replace(needle, insert, 1)
    path.write_text(text, encoding="utf-8")


def patch_synthetic():
  path = REPO / "tools/agent/run_hook_synthetic_tests.py"
  text = path.read_text(encoding="utf-8")
  marker = "        # 2 second bootstrap deny"
  block = (SRC / "synthetic_block.py").read_text(encoding="utf-8")
  if "31-std-spec-analyzing" not in text:
      text = text.replace(marker, block + "\n        " + marker, 1)
  path.write_text(text, encoding="utf-8")


def patch_manual():
    path = REPO / "docs/governance/CURSOR-LEAN-HARNESS-MANUAL-SETUP.md"
    append = (SRC / "manual.md").read_text(encoding="utf-8")
    text = path.read_text(encoding="utf-8")
    if "SPECIFICATION" not in text.split("write_scope:")[-1][:400]:
        if not text.endswith("\n"):
            text += "\n"
        text += "\n" + append
    path.write_text(text, encoding="utf-8")


def main():
    for src_name, rel in MAP.items():
        shutil.copyfile(SRC / src_name, REPO / rel)
    (REPO / "tools/agent/promote-spec-kit-feature").chmod(0o755)
    patch_shell_policy()
    patch_synthetic()
    patch_manual()
    probe = REPO / "tools/agent_policy/_bootstrap_probe.txt"
    if probe.is_file():
        probe.unlink()
    for rel in MAP.values():
        if rel.endswith(".py"):
            py_compile.compile(str(REPO / rel), doraise=True)
    py_compile.compile(str(REPO / "tools/agent/run_hook_synthetic_tests.py"), doraise=True)
    print("apply_all: ok")


if __name__ == "__main__":
    main()
