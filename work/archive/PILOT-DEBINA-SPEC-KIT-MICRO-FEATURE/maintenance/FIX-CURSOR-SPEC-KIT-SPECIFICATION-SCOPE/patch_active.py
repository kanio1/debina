from pathlib import Path
REPO = Path("/home/suso/debina")
path = REPO / "tools/agent_policy/active_state.py"
text = path.read_text(encoding="utf-8")
text = text.replace(
    'WRITE_SCOPES = ("WORK_ONLY", "IMPLEMENTATION", "REVIEW", "CLOSEOUT")',
    'WRITE_SCOPES = ("WORK_ONLY", "SPECIFICATION", "IMPLEMENTATION", "REVIEW", "CLOSEOUT")',
)
path.write_text(text, encoding="utf-8")
print("done")
