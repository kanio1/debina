#!/usr/bin/env python3
"""Conservative static trigger-overlap report; it does not claim routing proof."""
from __future__ import annotations
import re, sys
from itertools import combinations
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; SKILLS=ROOT/'.agents/skills'
if not SKILLS.is_dir(): SKILLS=ROOT/'.claude/skills'
STOP={'use','when','for','the','and','or','a','an','to','with','this','that','do','not','of','in','on','all','any','from','only'}
data=[]
for p in sorted(SKILLS.glob('*/SKILL.md')):
 m=re.search(r"^description:\s*[\"']?(.*?)[\"']?\s*$",p.read_text(),re.M); d=m.group(1) if m else ''
 data.append((p.parent.name,d,{x for x in re.findall(r'[a-z0-9]+',d.lower()) if x not in STOP}))
warnings=[]; failures=[]
for (a,da,ta),(b,db,tb) in combinations(data,2):
 score=len(ta&tb)/max(1,min(len(ta),len(tb)))
 if da==db: failures.append(f'identical descriptions: {a}, {b}')
 elif score>=.80: failures.append(f'near-identical token overlap {score:.2f}: {a}, {b}')
 elif score>=.55: warnings.append(f'high overlap {score:.2f}: {a}, {b}')
total=sum(len(d) for _,d,_ in data); longest=max(data,key=lambda x:len(x[1]))
print(f'DESCRIPTIONS: active={len(data)} total_chars={total} longest={longest[0]}:{len(longest[1])} target≈6000')
for w in warnings: print('WARN '+w)
if failures: print('DESCRIPTIONS: FAIL'); print('\n'.join(failures)); sys.exit(1)
print('DESCRIPTIONS: PASS (heuristic only; routing requires behavioral evaluation)')
