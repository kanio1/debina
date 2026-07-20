#!/usr/bin/env python3
from __future__ import annotations
import json,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; spec=json.loads(Path(sys.argv[1]).read_text())
text=(ROOT/spec['path']).read_text(encoding='utf-8').lower(); errors=[]
for phrase in spec.get('contains',[]):
 if phrase.lower() not in text: errors.append('missing: '+phrase)
for phrase in spec.get('not_authoritative',[]):
 if re.search(phrase,text,re.I): errors.append('forbidden: '+phrase)
if errors: print(f'ASSERTIONS: FAIL {spec["skill"]}'); print('\n'.join(errors)); sys.exit(1)
print(f'ASSERTIONS: PASS {spec["skill"]}')
