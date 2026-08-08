import re
from pathlib import Path
REPO = Path("/home/suso/debina")
path = REPO / "tools/agent_policy/write_gate.py"
text = path.read_text(encoding="utf-8")
if "evaluate_specification_path" not in text:
    text = text.replace(
        "from .active_state import (\n    PROFILE_RANK,\n    ActiveState,\n    analysis_phase,\n    implementation_approved,\n    implementation_phase,\n    load_active,\n    normalize_active,\n    repo_root,\n    wrapper_trusted,\n)",
        "from .active_state import (\n    PROFILE_RANK,\n    ActiveState,\n    analysis_phase,\n    implementation_approved,\n    implementation_phase,\n    load_active,\n    maintenance_paths,\n    normalize_active,\n    repo_root,\n    specification_scope,\n    validate_state_combination,\n    wrapper_trusted,\n)\nfrom .spec_kit import evaluate_specification_path, is_implementation_source_path",
    )
new_eval = Path(__file__).with_name("wg_eval.txt").read_text(encoding="utf-8")
text, n = re.subn(r"def evaluate_write\([\s\S]*?\n    return \"allow\", \"implementation path ok\"\n", new_eval, text, count=1)
if n:
    path.write_text(text, encoding="utf-8")
    print("write_gate patched")
else:
    print("write_gate patch failed")
