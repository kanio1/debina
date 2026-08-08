import re
from pathlib import Path
p = Path("/home/suso/debina/tools/agent_policy/active_state.py")
text = p.read_text(encoding="utf-8")
new_func = Path(__file__).with_name("derive_func.txt").read_text(encoding="utf-8")
text, n = re.subn(r"def _derive_approval_state\([\s\S]*?return \"PENDING\"\n", new_func, text, count=1)
if n:
    p.write_text(text, encoding="utf-8")
    print("derive patched")
else:
    print("derive not found")
