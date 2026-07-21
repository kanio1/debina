#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
bash tools/agent-config/validate-agent-instructions.sh
python3 tools/agent-config/generate-story-inventory.py --check
python3 tools/agent-config/validate-story-inventory.py
python3 tools/agent-config/validate-capability-graph.py
bash tools/skills/validate-all-skills.sh
python3 tools/requirements/validate-use-case-traceability.py
python3 tools/requirements/validate-planning-semantics.py
python3 tools/requirements/validate-source-traceability.py
python3 tools/architecture/validate-module-catalog.py
python3 tools/architecture/validate-adr-lifecycle.py
