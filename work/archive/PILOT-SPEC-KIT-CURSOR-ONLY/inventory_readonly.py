#!/usr/bin/env python3
"""Read-only inventory helper for Spec Kit pilot (no repo mutations)."""
from __future__ import annotations

import hashlib
import json
import os
import sys
from pathlib import Path


def inventory(root: Path) -> dict:
    files = []
    links = []
    for p in sorted(root.rglob("*")):
        rel = str(p.relative_to(root))
        if p.is_symlink():
            links.append({"path": rel, "target": os.readlink(p)})
        elif p.is_file():
            data = p.read_bytes()
            files.append(
                {
                    "path": rel,
                    "sha256": hashlib.sha256(data).hexdigest(),
                    "size": len(data),
                }
            )
    return {
        "root": str(root),
        "links": links,
        "files": files,
        "file_count": len(files),
        "link_count": len(links),
    }


def main() -> int:
    root = Path(sys.argv[1]).resolve()
    out = inventory(root)
    print(json.dumps(out, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
