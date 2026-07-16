#!/usr/bin/env bash
# Validates the SEPA Nexus database skill set (postgres-rls-migration,
# sepa-nexus-flyway-safe-change, sepa-nexus-payments-data-integrity,
# sepa-nexus-database-testing, sepa-nexus-database-review).
# No production dependency: pure bash + coreutils. Exits non-zero on any violation.
set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT" || exit 1

fail=0
ok()   { printf 'OK    %s\n' "$*"; }
err()  { printf 'FAIL  %s\n' "$*"; fail=1; }

echo "== SEPA Nexus database skill validator =="
echo "repo: $REPO_ROOT"
echo

SKILLS_DIR=".claude/skills"
REQUIRED_SKILLS=(
  "postgres-rls-migration"
  "sepa-nexus-flyway-safe-change"
  "sepa-nexus-payments-data-integrity"
  "sepa-nexus-database-testing"
  "sepa-nexus-database-review"
)

# ---------------------------------------------------------------------------
# 1. required skill directories and SKILL.md files exist
# ---------------------------------------------------------------------------
for skill in "${REQUIRED_SKILLS[@]}"; do
  dir="$SKILLS_DIR/$skill"
  md="$dir/SKILL.md"
  if [[ -d "$dir" ]]; then
    ok "$skill directory exists"
  else
    err "$skill directory is missing ($dir)"
    continue
  fi
  if [[ -f "$md" ]]; then
    ok "$skill/SKILL.md exists"
  else
    err "$skill/SKILL.md is missing"
    continue
  fi

  # frontmatter name + description
  if ! head -n 1 "$md" | grep -q '^---$'; then
    err "$skill/SKILL.md does not start with '---' frontmatter delimiter"
  else
    fm="$(sed -n '2,/^---$/p' "$md" | sed '$d')"
    if echo "$fm" | grep -qE '^name:\s*'"$skill"'\s*$'; then
      ok "$skill/SKILL.md frontmatter 'name' matches directory name"
    else
      err "$skill/SKILL.md frontmatter 'name' missing or does not match directory name '$skill'"
    fi
    if echo "$fm" | grep -qE '^description:\s*\S'; then
      ok "$skill/SKILL.md frontmatter has a non-empty 'description'"
    else
      err "$skill/SKILL.md frontmatter 'description' missing or empty"
    fi
  fi
done

echo

# ---------------------------------------------------------------------------
# 2. every referenced references/*.md file actually exists
# ---------------------------------------------------------------------------
for skill in "${REQUIRED_SKILLS[@]}"; do
  md="$SKILLS_DIR/$skill/SKILL.md"
  [[ -f "$md" ]] || continue
  refs="$(grep -oE 'references/[A-Za-z0-9_.-]+\.md' "$md" | sort -u)"
  if [[ -z "$refs" ]]; then
    err "$skill/SKILL.md references no references/*.md files (expected at least one)"
    continue
  fi
  missing=0
  while IFS= read -r ref; do
    [[ -z "$ref" ]] && continue
    if [[ ! -f "$SKILLS_DIR/$skill/$ref" ]]; then
      err "$skill/SKILL.md references '$ref' but $SKILLS_DIR/$skill/$ref does not exist"
      missing=1
    fi
  done <<< "$refs"
  if [[ $missing -eq 0 ]]; then
    count="$(echo "$refs" | grep -c . || true)"
    ok "$skill: all $count referenced references/*.md file(s) exist"
  fi

  # also check for orphaned reference files not linked from SKILL.md
  if [[ -d "$SKILLS_DIR/$skill/references" ]]; then
    while IFS= read -r -d '' f; do
      base="references/$(basename "$f")"
      if ! grep -qF "$base" "$md"; then
        err "$skill/$base exists on disk but is not referenced from SKILL.md"
      fi
    done < <(find "$SKILLS_DIR/$skill/references" -name '*.md' -print0 2>/dev/null)
  fi
done

echo

# ---------------------------------------------------------------------------
# 3. no duplicate copy under tools/codex; symlink resolves to .claude/skills
# ---------------------------------------------------------------------------
if [[ -e "tools/codex/.agents/skills" ]]; then
  target="$(readlink -f "tools/codex/.agents/skills" 2>/dev/null || true)"
  expected="$(readlink -f "$SKILLS_DIR" 2>/dev/null || true)"
  if [[ "$target" == "$expected" && -n "$target" ]]; then
    ok "tools/codex/.agents/skills resolves to $SKILLS_DIR (shared, not duplicated)"
  else
    err "tools/codex/.agents/skills does not resolve to $SKILLS_DIR (target: '$target', expected: '$expected')"
  fi
else
  err "tools/codex/.agents/skills does not exist"
fi

for skill in "${REQUIRED_SKILLS[@]}"; do
  if [[ -d "tools/codex/skills/$skill" ]] || [[ -d "tools/codex/.agents/skills-real/$skill" && ! -L "tools/codex/.agents/skills" ]]; then
    err "$skill appears duplicated under tools/codex/ outside the symlink"
  fi
done
ok "no database skill duplicated under tools/codex/ outside the shared symlink"

echo

# ---------------------------------------------------------------------------
# 4. forbidden instructions must not appear in any database skill file
# ---------------------------------------------------------------------------
check_no_positive_instruction() {
  local pattern="$1" label="$2"
  local hits=()
  for skill in "${REQUIRED_SKILLS[@]}"; do
    dir="$SKILLS_DIR/$skill"
    [[ -d "$dir" ]] || continue
    while IFS= read -r -d '' f; do
      while IFS= read -r line; do
        # skip markdown table rows -- they enumerate what a role/column has or
        # lacks under a header (e.g. a "Never has" column), not a standalone
        # instruction, and skip checklist items already phrased as a negative
        # ("- [ ] No ...")
        case "$line" in
          '|'*) continue ;;
          *'- [ ] No '*|*'- [ ] Never '*) continue ;;
        esac
        if echo "$line" | grep -qiE "$pattern"; then
          if ! echo "$line" | grep -qiE '\bnever\b|\bno\b|\bforbidden\b|do not|don.t|prohibit|rejected|reverted|removed|cannot'; then
            hits+=("$f: $line")
          fi
        fi
      done < "$f"
    done < <(find "$dir" -name '*.md' -print0)
  done
  if [[ ${#hits[@]} -gt 0 ]]; then
    err "found a positive (non-prohibiting) instance of forbidden pattern '$label': ${hits[0]}"
  else
    ok "no positive instruction found for forbidden pattern '$label'"
  fi
}

check_no_positive_instruction 'BYPASSRLS' 'app role granted BYPASSRLS'
check_no_positive_instruction '\b(FLOAT|DOUBLE PRECISION)\b.{0,40}(money|amount|currency)' 'FLOAT/DOUBLE for money'
check_no_positive_instruction 'edit (an? )?already[- ]applied migration' 'editing an already-applied migration'
check_no_positive_instruction '\bact\b.{0,40}(test|verify|gate)' 'act used as a test/verification gate'
check_no_positive_instruction 'grant.{0,40}(SELECT|INSERT|UPDATE|DELETE).{0,40}(another|foreign|other) module' 'cross-schema repository/role access'
check_no_positive_instruction '\bDROP\b.{0,20}\bTABLE\b|\bTRUNCATE\b' 'automatic DROP/TRUNCATE'

echo

# ---------------------------------------------------------------------------
# 5. content spot-checks per skill (key terms that must be present)
# ---------------------------------------------------------------------------
check_contains() {
  local skill="$1" pattern="$2" label="$3"
  local dir="$SKILLS_DIR/$skill"
  if grep -rqE "$pattern" "$dir" 2>/dev/null; then
    ok "$skill contains required term: $label"
  else
    err "$skill is missing required term: $label"
  fi
}

check_contains "postgres-rls-migration" "WITH CHECK" "WITH CHECK"
check_contains "postgres-rls-migration" "USING" "USING"
check_contains "postgres-rls-migration" "FORCE ROW LEVEL SECURITY" "FORCE ROW LEVEL SECURITY"
check_contains "sepa-nexus-database-testing" "Testcontainers" "Testcontainers"
check_contains "sepa-nexus-database-review" "[Vv]erdict" "verdict"
check_contains "sepa-nexus-database-review" "[Bb]locking findings" "blocking findings"
check_contains "sepa-nexus-database-review" "Testcontainers evidence" "Testcontainers evidence"

echo
if [[ $fail -ne 0 ]]; then
  echo "RESULT: FAIL"
  exit 1
fi
echo "RESULT: PASS"
exit 0
