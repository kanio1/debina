# Dagger Pipeline Architecture

Future pipeline implementation is one Go SDK Dagger module. It owns execution logic and exposes `fast`, `integration`, `smoke`, and `all` checks. `fast` runs deterministic format/lint/unit/governance gates; `integration` adds Testcontainers/integration evidence; `smoke` runs the approved minimal runtime smoke; `all` composes the supported suite and produces declared artifacts.

GitHub Actions remains thin: pull request → `fast`; main push → `integration`; nightly/manual → `all`; release → `all` plus artifacts. Actions own triggers and credentials, never duplicate command orchestration. `act` may validate workflow behaviour but is not the primary application test runner. No Dagger module or workflow change is implemented by this constitution rebase.
