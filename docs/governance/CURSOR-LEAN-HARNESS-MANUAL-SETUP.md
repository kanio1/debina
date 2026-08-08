# Cursor Lean Harness — Manual Setup

Agent-configured repository pieces live under `.cursor/`, `work/`, `tools/agent/`, and `.claude/skills/`. This page lists only steps a human must perform in the IDE/CLI — the agent cannot reliably complete them.

## 1. Mode selection

- Use **Ask / read-only** for discovery and independent review.
- Use **Agent** for the single writer on an approved active task.
- Do not leave Auto-Run in a mode that allows `git commit`, `git push`, or `git merge`.

## 2. Cursor Settings

1. Open **Cursor Settings → Hooks** and confirm project hooks from `.cursor/hooks.json` are loaded (`command-guard`, `shell-post-check`, `scope-approval-gate`, `checkpoint`).
2. Open **Cursor Settings → Rules** and confirm the project rules under `.cursor/rules/` appear (`00-core` always-on).
3. Confirm **Skills** resolve from `.claude/skills` (and the existing `.cursor/skills` / `.agents/skills` symlinks). Do not create a second physical skills tree.
4. Keep sandbox / network restricted for agent terminals; allow localhost only when needed for local app checks.

## 3. Auto-Run and command allowlist

1. Prefer allowlist / ask-on-unknown for shell.
2. Allow the agent wrappers explicitly (or use `bash tools/agent/<name>`):
   - `task-status`, `verify-fast`, `verify-task`, `final-check`, `health-check`
   - `bootstrap-task`, `approve-task`, `set-task-state`, `closeout-task`
3. Deny destructive git/rm/sudo and Shell source mutations; hooks enforce path/approval context.
4. Review untracked `.cursor/permissions.json` if present: it currently lists permissive Git write prefixes that conflict with the lean deny-first policy — tighten manually or remove.

Project CLI overlay: `.cursor/cli.json` (deny precedes allow). Home CLI config remains `~/.cursor/cli-config.json`; restart the CLI after edits.

## 4. Executable bits

If wrappers/hooks are not executable, run once locally:

```bash
chmod +x tools/agent/task-status tools/agent/verify-fast tools/agent/verify-task tools/agent/final-check tools/agent/health-check
chmod +x .cursor/hooks/command-guard.py .cursor/hooks/scope-approval-gate.py .cursor/hooks/checkpoint.py .cursor/hooks/health-check.py
```

Do not ask the agent to bypass the command guard to obtain `chmod`.

## 5. MCP (manual only)

Do **not** install NotebookLM MCP, GitHub MCP (unless on demand), write-capable PostgreSQL MCP, persistent memory MCP, or Serena.

Never paste secrets into MCP config committed to the repo. Project MCP entries live in `.cursor/mcp.json`.

### Context7 / Next DevTools / PostgreSQL / Kafka MCP

#### Context7 (OAuth)

1. Project entry is already in `.cursor/mcp.json`:

   ```json
   "context7": {
     "url": "https://mcp.context7.com/mcp/oauth"
   }
   ```

2. Restart Cursor (or reload MCP servers).
3. Open **Cursor Settings → MCP**, select `context7`, and complete **Authenticate** in the browser.
4. Do not store API keys in the repository. Use MCP mode only (no Context7 Skill install).

Before querying Context7, read the exact library version from this repo (`backend/pom.xml`, effective Maven model, `frontend/package.json`). Resolve the correct library ID; reject similarly named wrong owners and never substitute an older major silently.

#### Next.js DevTools

Configured in `.cursor/mcp.json` as `next-devtools` (`npx -y next-devtools-mcp@latest`). Use for live Next.js runtime facts when `pnpm dev` is running. Context7 remains the source for version-pinned docs (`frontend/package.json` → Next.js `16.2.10`, TypeScript `6.0.3`). Do not run automatic codemods or upgrades from DevTools.

#### Runtime Node pin (Kafka + PostgreSQL wrappers)

Kafka and PostgreSQL MCP wrappers **must** run on the user’s Node 24, not Cursor’s bundled Node:

- Absolute Node: `/home/suso/.nvm/versions/node/v24.18.0/bin/node`
- Version: `v24.18.0`
- ABI (`process.versions.modules`): `137`

Wrappers (`tools/mcp/start-kafka-mcp.sh`, `tools/mcp/start-dbhub.sh`) invoke that Node + its `npx-cli.js`, put `${NODE_HOME}/bin` first on `PATH`, and pin packages (`@confluentinc/mcp-confluent@1.5.0`, `@bytebase/dbhub@0.24.0`). Each MCP uses an isolated npm cache under `~/.cache/debina-mcp/{kafka|postgres}/<version>/node-v24-abi-137/` — never the shared `~/.npm/_npx` cache for these servers. Do not use `@latest` in those wrappers.

#### PostgreSQL (DBHub, read-only)

1. Start app Postgres (not Keycloak DB):

   ```bash
   podman compose -f infra/docker-compose.yml up -d postgres
   ```

2. Bootstrap a dedicated role `debina_mcp_ro` on `sepa_nexus` with
   `NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS`,
   `default_transaction_read_only=on`, `statement_timeout=10s`, and
   `GRANT CONNECT` plus `USAGE`/`SELECT` on non-system schemas only (no write grants, no `BYPASSRLS`).

3. Store the DSN **outside the repo** only:

   - `~/.config/debina/` mode `700`
   - `~/.config/debina/postgres-mcp.env` mode `600` with
     `DEBINA_MCP_POSTGRES_DSN='postgresql://debina_mcp_ro:…@localhost:5432/sepa_nexus?sslmode=disable'`
   - Never commit the real DSN or password.

4. Copy `docs/governance/mcp/dbhub.toml.example` to `~/.config/debina/dbhub.toml` (mode `600`). Keep `dsn = "${DEBINA_MCP_POSTGRES_DSN}"`, `readonly = true`, `max_rows = 200`, and timeouts. Do not embed the full DSN in the TOML.

5. `.cursor/mcp.json` entry `postgres-debina` runs `bash /home/suso/debina/tools/mcp/start-dbhub.sh` (no secrets in mcp.json).

Read-only is enforced by: dedicated role, `default_transaction_read_only`, DBHub `readonly = true`, row/timeout limits, and Cursor approvals for risky SQL.

#### Kafka (Confluent MCP, read-only)

1. Start local Kafka (`PLAINTEXT://localhost:9092`):

   ```bash
   podman compose -f infra/docker-compose.yml up -d kafka
   ```

2. Copy `docs/governance/mcp/mcp-confluent.yaml.example` to `~/.config/debina/mcp-confluent.yaml` (mode `600`). Keep `read_only: true` and `security.protocol: PLAINTEXT` — no API keys or SASL credentials for the local cluster.

3. `.cursor/mcp.json` entry `kafka-debina` runs `bash /home/suso/debina/tools/mcp/start-kafka-mcp.sh`.

#### Verify MCP after setup

1. Run **Developer: Reload Window** (or restart Cursor) so `.cursor/mcp.json` is reloaded.
2. Approve/enable servers if needed:

   ```bash
   cursor-agent mcp enable kafka-debina
   cursor-agent mcp enable postgres-debina
   ```

3. Then:

   ```bash
   cursor-agent mcp list
   cursor-agent mcp list-tools kafka-debina
   cursor-agent mcp list-tools postgres-debina
   ```

Expect `kafka-debina: ready` and `postgres-debina: ready` with tools listed. Kafka must not report ABI/`NODE_MODULE_VERSION` 127. PostgreSQL must not report a missing `postgres-mcp.env`.

- `context7` requires OAuth; leave its mcp.json entry unchanged.
- Do not run write/admin operations through PostgreSQL or Kafka MCP.
- Never store secrets under the repository.

## 6. Phased approval and write scope

Canonical ACTIVE fields:

```text
policy_profile: FAST | STANDARD | DECISION
phase: BOOTSTRAP | ANALYZING | SPEC_READY | IMPLEMENTING | VERIFYING | REVIEWING | CLOSING | BLOCKED | COMPLETED
approval_state: NOT_REQUIRED | PENDING | APPROVED | REJECTED
write_scope: WORK_ONLY | SPECIFICATION | IMPLEMENTATION | REVIEW | CLOSEOUT
lane: backward-compat mirror of policy_profile only
```

Rules (normative):

```text
STANDARD analysis does not require implementation approval.
STANDARD implementation requires explicit approval.
FAST is not a security bypass.
A policy profile is not a write permission.
Shell must not be used to bypass structured file-write controls.
Build and test outputs are not equivalent to source mutations.
Hooks protect boundaries.
Skills and Spec Kit drive workflow.
```

- `ANALYZING` + `WORK_ONLY` may write `work/active/<ACTIVE_TASK>/**` without `.approved`.
- `ANALYZING` + `SPECIFICATION` may write bound active feature artifacts:
  - `work/active/<ACTIVE_TASK>/**`
  - `.specify/feature.json`
  - `specs/<BOUND_FEATURE>/**`
- `SPECIFICATION` does **not** grant implementation permission.
- For STANDARD and DECISION, `approval_state=PENDING` during `ANALYZING` and `SPEC_READY` is normal.
  `PENDING` blocks implementation; it does not block specification artifacts when `write_scope=SPECIFICATION`.
- At `SPEC_READY`, scope returns to `WORK_ONLY` while implementation approval remains pending.
- `work/ACTIVE.json` is task authority. `.specify/feature.json` is feature metadata only.
- Only the feature bound to the active Debina task may be modified.
- Planning permission is not implementation permission.
- `ACTIVE.json` / `QUEUE.md` updates go through `tools/agent/bootstrap-task`, `set-task-state`, `approve-task`, `closeout-task`.
- `IMPLEMENTING` for STANDARD/DECISION requires `approval_state=APPROVED` **and** `allowed_paths`.
- Profile downgrade requires `set-task-state --allow-downgrade` with justification in `progress.md`.
- Shell pre-check blocks redirects/`tee`/`sed -i`/interpreter writes to tracked sources; post-check reports `UNAUTHORIZED_SHELL_WRITE_DETECTED` without auto-restore.

## 7. Manual approvals

For STANDARD implementation:

```text
work/approvals/<TASK-ID>.approved
```

For DECISION:

```text
work/approvals/<TASK-ID>.decision.approved
work/approvals/<TASK-ID>.implementation.approved
```

Copy from `work/templates/approval-template.txt`. Humans author these files; agents must not forge them.
Then run `./tools/agent/approve-task` (or `bash tools/agent/approve-task`).

`.approved` means implementation approval only — not analysis permission.

## 8. Pilot workflow

1. Prefer `bash tools/agent/bootstrap-task --task-id … --policy-profile …`.
2. Or `/debina-discover-and-specify` → ACTIVE + plan/progress with phased fields.
3. Human approval when entering STANDARD/DECISION implementation.
4. `/debina-implement-and-verify` → wrappers only for verify.
5. `/debina-review-and-next-work` (Ask mode) → one `NEXT`, ≤2 `LATER`; or `bash tools/agent/closeout-task`.
6. Human runs any `git commit` explicitly when ready.

## 9. Smoke commands (human)

```bash
bash tools/agent/task-status
bash tools/agent/health-check
bash tools/agent/verify-fast
python3 tools/agent/run_hook_synthetic_tests.py
```

### CLI permission caveat

`.cursor/cli.json` is a coarse first layer (allow wrappers; deny obvious git writes / `sed -i` / secret Writes). Contextual enforcement of phase, approval, and `allowed_paths` remains in `.cursor/hooks/**` via `tools/agent_policy`. Hooks are fail-closed for Write/Shell guards.
