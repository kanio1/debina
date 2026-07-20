#!/usr/bin/env python3
from __future__ import annotations
import json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
reg=json.loads((ROOT/'planning/skills/skills-registry.yaml').read_text())
entries={x['name']:x for x in reg['skills']}; active={x['name']:x for x in reg['skills'] if x['status']=='ACTIVE'}
errors=[]
for name,item in active.items():
 authoring=ROOT/item['path']/'SKILL.md'
 discovery=ROOT/'.agents/skills'/name/'SKILL.md'
 if not authoring.is_file(): errors.append(f'active registry authoring entry missing skill: {name}')
 if not discovery.is_file(): errors.append(f'active registry entry missing Codex discovery path: {name}')
 elif authoring.is_file() and discovery.resolve() != authoring.resolve(): errors.append(f'active registry discovery path is not the authoring source: {name}')
 for e in item.get('evals',[]):
  if not (ROOT/e).is_file(): errors.append(f'missing eval for {name}: {e}')
for name,item in entries.items():
 if item['status']=='PLANNED' and (ROOT/'.agents/skills'/name).is_dir(): errors.append(f'planned skill has active canonical dir: {name}')
if len(entries)!=len(set(entries)): errors.append('duplicate registry names')
if errors: print('REGISTRY: FAIL'); print('\n'.join(errors)); sys.exit(1)
print(f'REGISTRY: PASS ({len(active)} active through .agents/skills, {sum(x["status"]=="PLANNED" for x in reg["skills"])} planned)')
