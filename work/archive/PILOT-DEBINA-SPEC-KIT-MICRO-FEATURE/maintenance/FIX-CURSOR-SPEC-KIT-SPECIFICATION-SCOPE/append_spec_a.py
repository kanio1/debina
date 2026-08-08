from pathlib import Path
p = Path("/home/suso/debina/tools/agent_policy/spec_kit.py")
chunk = Path(__file__).with_name("spec_chunk_a.txt").read_text(encoding="utf-8")
p.write_text(p.read_text(encoding="utf-8") + chunk, encoding="utf-8")
print("append a")
