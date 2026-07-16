#!/usr/bin/env bash
# Local aggregator for all SEPA Nexus governance/planning validators. Run this
# before committing any change to AGENTS.md/CLAUDE.md, planning/, or
# .claude/skills/. Does not run Maven/pnpm -- this checks governance and
# planning artifacts only, never application code. Never invokes `act`.
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT" || exit 1

echo "###############################################################"
echo "# 1/4 — agent instruction hierarchy"
echo "###############################################################"
bash tools/agent-config/validate-agent-instructions.sh

echo
echo "###############################################################"
echo "# 2/4 — story inventory"
echo "###############################################################"
python3 tools/agent-config/validate-story-inventory.py

echo
echo "###############################################################"
echo "# 3/4 — capability graph"
echo "###############################################################"
python3 tools/agent-config/validate-capability-graph.py

echo
echo "###############################################################"
echo "# 4/4 — database skills"
echo "###############################################################"
bash tools/agent-config/validate-database-skills.sh

echo
echo "RESULT: PASS (all 4 governance validators passed)"
