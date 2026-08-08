from pathlib import Path
import py_compile

REPO = Path("/home/suso/debina")

def w(rel, content):
    dest = REPO / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(content, encoding="utf-8")
    print("wrote", rel)
