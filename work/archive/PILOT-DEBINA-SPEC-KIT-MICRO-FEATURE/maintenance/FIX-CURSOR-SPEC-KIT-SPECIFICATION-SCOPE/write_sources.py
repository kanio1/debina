from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[3]


def emit(name, text):
    (ROOT / "sources" / name).write_text(text, encoding="utf-8")


def main():
    (ROOT / "sources").mkdir(exist_ok=True)
    emit("spec_kit.py", SPEC_KIT)
    emit("active_state.py", ACTIVE_STATE)
    emit("write_gate.py", WRITE_GATE)
    emit("set_task_state.py", SET_TASK_STATE)
    emit("promote_spec_kit_feature.py", PROMOTE)
    emit("promote-spec-kit-feature", PROMOTE_SH)
    emit("health-check.py", HEALTH)
    emit("shell_policy.patch", SHELL_PATCH)
    emit("synthetic_append.py", SYNTHETIC)
    emit("manual_setup.append.md", MANUAL)
    print("sources ready")


SPEC_KIT = "SPEC_KIT_PLACEHOLDER"
ACTIVE_STATE = "ACTIVE_PLACEHOLDER"
WRITE_GATE = "WRITE_GATE_PLACEHOLDER"
SET_TASK_STATE = "SET_PLACEHOLDER"
PROMOTE = "PROMOTE_PLACEHOLDER"
PROMOTE_SH = "PROMOTE_SH_PLACEHOLDER"
HEALTH = "HEALTH_PLACEHOLDER"
SHELL_PATCH = "SHELL_PLACEHOLDER"
SYNTHETIC = "SYNTHETIC_PLACEHOLDER"
MANUAL = "MANUAL_PLACEHOLDER"

if __name__ == "__main__":
    main()
