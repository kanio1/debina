from pathlib import Path
p = Path("/home/suso/debina/tools/agent_policy/spec_kit.py")
chunk = Path(__file__).with_name("spec_chunk_c.txt").read_text(encoding="utf-8")
p.write_bytes(p.read_bytes() + chunk.encode("utf-8"))
print("append c")
