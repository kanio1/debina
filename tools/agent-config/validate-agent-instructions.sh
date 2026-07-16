#!/usr/bin/env bash
# Validates the AGENTS.md/CLAUDE.md instruction hierarchy for SEPA Nexus.
# No production dependency: pure bash + coreutils + git. Exits non-zero on any violation.
set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT" || exit 1

fail=0
ok()   { printf 'OK    %s\n' "$*"; }
warn() { printf 'WARN  %s\n' "$*"; }
err()  { printf 'FAIL  %s\n' "$*"; fail=1; }

echo "== SEPA Nexus agent instruction validator =="
echo "repo: $REPO_ROOT"
echo

# ---------------------------------------------------------------------------
# Collect active instruction files (root AGENTS.md/CLAUDE.md + local pairs).
# Excludes node_modules/target/.git and anything under a skills directory,
# which may legitimately quote these strings as documentation/examples.
# ---------------------------------------------------------------------------
INSTRUCTION_FILES=()
while IFS= read -r -d '' f; do
  INSTRUCTION_FILES+=("$f")
done < <(find . \
  \( -path './node_modules' -o -path '*/node_modules' -o -path './target' \
     -o -path '*/target' -o -path './.git' -o -path './.claude/skills' \
     -o -path './tools/codex/.agents' \) -prune \
  -o \( -name 'AGENTS.md' -o -name 'CLAUDE.md' \) -type f -print0)

if [[ ${#INSTRUCTION_FILES[@]} -eq 0 ]]; then
  err "no AGENTS.md/CLAUDE.md files found at all"
fi

# ---------------------------------------------------------------------------
# 1. root CLAUDE.md must start with @AGENTS.md
# ---------------------------------------------------------------------------
if [[ -f CLAUDE.md ]]; then
  first_line="$(head -n 1 CLAUDE.md)"
  if [[ "$first_line" == "@AGENTS.md" ]]; then
    ok "root CLAUDE.md starts with '@AGENTS.md'"
  else
    err "root CLAUDE.md's first line is '$first_line', expected '@AGENTS.md'"
  fi
else
  err "root CLAUDE.md is missing"
fi

# ---------------------------------------------------------------------------
# 2. stale historical statements must not appear in any active instruction file
# ---------------------------------------------------------------------------
echo
STALE_PATTERNS=(
  "the first commit exists"
  "no application code yet"
  "active implementation scope remains EPIC-00"
  "This is Iteration 0"
)
for pattern in "${STALE_PATTERNS[@]}"; do
  hits=()
  for f in "${INSTRUCTION_FILES[@]}"; do
    grep -Fq -- "$pattern" "$f" 2>/dev/null && hits+=("$f")
  done
  if [[ ${#hits[@]} -gt 0 ]]; then
    err "stale statement '$pattern' found in: ${hits[*]}"
  else
    ok "no stale statement: '$pattern'"
  fi
done

# ---------------------------------------------------------------------------
# 3. frontend instructions must never *instruct* running npm build/lint
#    (a sentence that forbids npm, e.g. "never run npm install", must NOT
#    count as an instruction to use it — only scan non-prohibition lines;
#    also require a word boundary so "pnpm run build" isn't matched as a
#    substring of "npm run build")
# ---------------------------------------------------------------------------
echo
non_prohibition() {
  grep -viE 'never run|do not run|don.t run|not run|forbidden|no exceptions' "$1" 2>/dev/null
}

npm_build_hits=()
for f in "${INSTRUCTION_FILES[@]}"; do
  case "$f" in
    ./frontend/*)
      non_prohibition "$f" | grep -Eq '(^|[^p])\bnpm run (build|lint|typecheck)\b' && npm_build_hits+=("$f")
      ;;
  esac
done
if [[ ${#npm_build_hits[@]} -gt 0 ]]; then
  err "npm run build/lint/typecheck instructed in frontend instruction file(s): ${npm_build_hits[*]} (this project is pnpm-only)"
else
  ok "no frontend instruction file tells you to run npm build/lint/typecheck"
fi

# ---------------------------------------------------------------------------
# 4. no single file should instruct both npm and pnpm command invocations
#    (again: skip lines that are prohibiting npm, not instructing it)
# ---------------------------------------------------------------------------
mixed_hits=()
for f in "${INSTRUCTION_FILES[@]}"; do
  has_npm=0; has_pnpm=0
  non_prohibition "$f" | grep -Eq '(^|[^p])\bnpm (run|install|ci)\b' && has_npm=1
  grep -Eq '\bpnpm (run|install)\b' "$f" 2>/dev/null && has_pnpm=1
  if [[ $has_npm -eq 1 && $has_pnpm -eq 1 ]]; then
    mixed_hits+=("$f")
  fi
done
if [[ ${#mixed_hits[@]} -gt 0 ]]; then
  err "file(s) instruct both npm and pnpm run/install commands: ${mixed_hits[*]}"
else
  ok "no instruction file instructs both npm and pnpm run/install commands"
fi

# ---------------------------------------------------------------------------
# 5. report sizes of all active instruction files
# ---------------------------------------------------------------------------
echo
echo "-- instruction file sizes --"
for f in "${INSTRUCTION_FILES[@]}"; do
  bytes="$(wc -c < "$f" | tr -d ' ')"
  printf '%6s bytes  %s\n' "$bytes" "$f"
done

# ---------------------------------------------------------------------------
# 6. detect permanent AGENTS.override.md files
# ---------------------------------------------------------------------------
echo
override_files=()
while IFS= read -r -d '' f; do
  override_files+=("$f")
done < <(find . -path './node_modules' -prune -o -path '*/node_modules' -prune \
  -o -name 'AGENTS.override.md' -type f -print0)
if [[ ${#override_files[@]} -gt 0 ]]; then
  warn "AGENTS.override.md present — verify these are intentional and temporary: ${override_files[*]}"
else
  ok "no AGENTS.override.md present"
fi

# ---------------------------------------------------------------------------
# 7. local AGENTS.md/CLAUDE.md wrapper pairs must exist for the four scoped dirs
# ---------------------------------------------------------------------------
echo
for dir in backend frontend infra planning; do
  a="$dir/AGENTS.md"
  c="$dir/CLAUDE.md"
  if [[ -f "$a" && -f "$c" ]]; then
    ok "$dir has both AGENTS.md and CLAUDE.md"
  else
    err "$dir is missing a wrapper file (AGENTS.md present: $( [[ -f "$a" ]] && echo yes || echo no ), CLAUDE.md present: $( [[ -f "$c" ]] && echo yes || echo no ))"
  fi
  if [[ -f "$c" ]]; then
    local_first="$(head -n 1 "$c")"
    if [[ "$local_first" != "@AGENTS.md" ]]; then
      err "$c does not start with '@AGENTS.md' (found: '$local_first')"
    fi
  fi
done

echo
if [[ $fail -ne 0 ]]; then
  echo "RESULT: FAIL"
  exit 1
fi
echo "RESULT: PASS"
exit 0
