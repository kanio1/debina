#!/usr/bin/env python3
from __future__ import annotations
import argparse,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; REQUIRED=('status','context','options','decision','consequences','confidence','review trigger','affected use cases','affected quality','reversibility')
def main():
 p=argparse.ArgumentParser(); p.add_argument('--path',default='docs/architecture'); a=p.parse_args(); errors=warnings=0
 for path in (ROOT/a.path).glob('ADR-*.md'):
  text=path.read_text().lower(); enforced='semantic_enforcement: enforced' in text or '[new-adr]' in text
  missing=[x for x in REQUIRED if x not in text]
  if missing:
   level='ERROR' if enforced else 'WARNING'; errors+=enforced; warnings+=not enforced; print(f"{level} ADR-001 {path.relative_to(ROOT)} lifecycle missing {', '.join(missing)}")
 print(f"SUMMARY errors={errors} warnings={warnings} validated_adrs={len(list((ROOT/a.path).glob('ADR-*.md')))}")
 return 1 if errors else 0
if __name__=='__main__': sys.exit(main())
