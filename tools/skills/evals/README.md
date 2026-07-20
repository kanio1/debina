# Debina skill evals

Routing fixtures are reusable prompt expectations, not proof that a model selected a skill. Each Wave 1 fixture contains at least four positive, four negative, two overlap and two pressure scenarios; preserved legacy skills retain compatibility fixtures.

Behavioral execution: `NOT_EXECUTED`. This environment exposes no documented safe non-recursive skill-evaluation command. A future evaluator should copy this repository to a disposable read-only worktree, inspect `codex --help` for an explicit skill-eval mode, run only that documented command with the fixture file and model/config recorded, then store raw output outside the repository unless deliberately approved.

Run deterministic checks from any directory with `bash tools/skills/validate-all-skills.sh`. Regression assertions check safety phrases, not model behavior.
