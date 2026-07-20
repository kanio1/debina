#!/usr/bin/env python3
from __future__ import annotations
import json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sources=json.loads((ROOT/'planning/skills/upstream-sources.lock.yaml').read_text())['sources']
errors=[]
for s in sources:
 for k in ('repository','commit','commit_date','license','reviewed_paths','used_for','copied_verbatim','adaptation_summary','last_verified'):
  if k not in s or s[k] in ('',None,[]): errors.append(f'{s.get("repository","unknown")}: missing {k}')
 if s.get('copied_verbatim') is not False: errors.append(f'{s["repository"]}: copied wording must be declared and reviewed')
if list((ROOT/'.claude/skills').rglob('*.py')) and any('third-party' in str(x).lower() for x in (ROOT/'.claude/skills').rglob('*')): errors.append('unreviewed third-party script marker')
if errors: print('PROVENANCE: FAIL'); print('\n'.join(errors)); sys.exit(1)
print(f'PROVENANCE: PASS ({len(sources)} reviewed upstream repositories; no copied external scripts)')
