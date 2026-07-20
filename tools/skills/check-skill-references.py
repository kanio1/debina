#!/usr/bin/env python3
"""Check direct local references in SKILL.md; never follows network links."""
from __future__ import annotations
import re, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; SKILLS=ROOT/'.agents/skills'
if not SKILLS.is_dir(): SKILLS=ROOT/'.claude/skills'
errors=[]
for md in sorted(SKILLS.glob('*/SKILL.md')):
 text=md.read_text(encoding='utf-8')
 if re.search(r'/(?:home|Users)/',text): errors.append(f'{md.relative_to(ROOT)} embeds a user-home path')
 if '/tmp/' in text: errors.append(f'{md.relative_to(ROOT)} presents /tmp as durable source')
 refs=set(re.findall(r'(?:references|scripts|assets)/[A-Za-z0-9_.\-/]+',text))
 for rel in refs:
  target=(md.parent/rel).resolve()
  if ROOT not in target.parents or not target.exists(): errors.append(f'{md.relative_to(ROOT)} missing/escaping {rel}')
 for link in re.findall(r'\]\(([^)]+)\)',text):
  if link.startswith(('http:','https:','#','mailto:')): continue
  if link.startswith('/') or link.startswith('~'): errors.append(f'{md.relative_to(ROOT)} absolute local link {link}')
if errors: print('REFERENCES: FAIL'); print('\n'.join(errors)); sys.exit(1)
print('REFERENCES: PASS (all direct local skill references resolve)')
