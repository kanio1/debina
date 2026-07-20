#!/usr/bin/env bash
set -euo pipefail
repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$repo_root"
echo "== Debina skills validation =="
for check in check-codex-discovery-path.py check-skill-frontmatter.py check-duplicate-skill-names.py check-skill-references.py check-skill-registry.py check-description-overlap.py check-skill-source-provenance.py; do
  echo "-- $check"
  python3 "tools/skills/$check"
done
for assertion in tools/skills/evals/regression/*.json; do
  python3 tools/skills/check-content-assertions.py "$assertion"
done
echo "RESULT: PASS"
