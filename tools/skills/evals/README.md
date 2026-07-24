# Debina skill evals

Routing fixtures are reusable prompt expectations, not proof that a model selected a skill. Each Wave 1 fixture contains at least four positive, four negative, two overlap and two pressure scenarios; preserved legacy skills retain compatibility fixtures.

Behavioral evidence is intentionally split: explicit discovery/invocation/reference loading/read-only behavior for `$debina-payment-state-finality` is `VERIFIED`; implicit routing is `NOT_EXECUTED`. Fixture files do not prove model routing. A future implicit-routing evaluator should use a disposable read-only worktree, inspect `codex --help` for a documented evaluator, record model/config and raw results, and avoid recursive or uncontrolled sessions.

`enterprise-use-case-engineering` additionally has deterministic routing,
regression, adversarial and cross-skill-chain contract evals. They prove fixture
coverage and skill guardrail presence, not autonomous model selection.

Run deterministic checks from any directory with
`bash tools/skills/validate-all-skills.sh`. Regression assertions check safety
phrases, not model behavior.
