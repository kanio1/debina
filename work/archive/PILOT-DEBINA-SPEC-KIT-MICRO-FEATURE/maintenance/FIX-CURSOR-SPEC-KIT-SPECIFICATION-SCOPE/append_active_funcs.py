from pathlib import Path
p = Path("/home/suso/debina/tools/agent_policy/active_state.py")
text = p.read_text(encoding="utf-8")
if "validate_state_combination" not in text:
    extra = Path(__file__).with_name("append_active_funcs.txt").read_text(encoding="utf-8")
    p.write_text(text.rstrip() + extra + "\n", encoding="utf-8")
print("active funcs")
