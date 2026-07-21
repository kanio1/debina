#!/usr/bin/env python3
from __future__ import annotations
import re, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
VALID={"domain-module","supporting-domain-module","generic-module","technical-adapter","integration-adapter","platform-module","read-model-adapter"}
def main():
 text=(ROOT/'docs/architecture/MODULE-CATALOG.yaml').read_text(); modules=re.findall(r"- \{name: ([^,]+), package: ([^,]+), module_type: ([^,]+).*?owned_schema: ([^,}]+)",text)
 errors=0; names=set(); writers={}
 for name,package,typ,schema in modules:
  if name in names: print(f"ERROR MOD-001 module={name} unique-id duplicate"); errors+=1
  names.add(name)
  if typ not in VALID: print(f"ERROR MOD-002 module={name} classification invalid {typ}"); errors+=1
  if package not in {'none','database-only-currently','frontend/app/api'} and not (ROOT/'backend/src/main/java'/Path(*package.split('.'))).exists(): print(f"ERROR MOD-003 module={name} package missing {package}"); errors+=1
  if schema not in {'none','undecided'}:
   if schema in writers: print(f"ERROR MOD-004 schema={schema} duplicate writers {writers[schema]},{name}"); errors+=1
   writers[schema]=name
 print(f"SUMMARY errors={errors} warnings=0 validated_modules={len(modules)}")
 return 1 if errors else 0
if __name__=='__main__': sys.exit(main())
