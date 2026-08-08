import shutil
from pathlib import Path
REPO = Path("/home/suso/debina")
src = Path(__file__).resolve().parent / "write_gate_full.py"
shutil.copyfile(src, REPO / "tools/agent_policy/write_gate.py")
print("write_gate installed")
