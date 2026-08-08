# Pre-install baseline — INSTALL-SPEC-KIT-CURSOR-IN-DEBINA

## Repo identity

| Field | Value |
|---|---|
| branch | `rebase/enterprise-evolution` |
| HEAD | `196d40596c0049c03d20889e83e6539463381758` |
| ACTIVE before bootstrap | absent |
| QUEUE NOW | `INSTALL-SPEC-KIT-CURSOR-IN-DEBINA` |
| hook health | PASS |
| skills sync `--check` | PASS (33==33) |
| `git diff --check` (standalone) | PASS |
| Spec Kit tag `v0.14.3` | `ec45316a2c240ae1a118bef596f2824c96eae9f8` |
| CLI | `uvx --from git+https://github.com/github/spec-kit.git@v0.14.3` → version `0.14.3` |
| persistent specify install | none |

## Finding preserved

```text
CURSOR_SKILLS_RUNTIME_DISCOVERY_SOURCE_AMBIGUOUS
```

27 Skills presented via `.agents/skills`; 33 bridges readable via `.cursor/skills`.

## verify-fast baseline (FAIL — pre-existing)

| Check | Result | Note |
|---|---|---|
| `git diff --check` inside verify-fast | FAIL | `PermissionError` / cannot hash `CLAUDE.md` |
| hygiene validator | FAIL | `PermissionError: CLAUDE.md` |
| `validate-handoff.py` | FAIL | HANDOFF-010 Resume vs next_action |
| `./mvnw … compile` | FAIL | Maven local repo sandbox `/root/.m2` |
| skills validation | PASS | |
| health-check | PASS | |
| `pnpm exec tsc` | (ran; no new Spec Kit issue) | |

## Paths present / absent

| Path | State |
|---|---|
| `.cursor/skills` | real directory (not symlink) |
| Debina bridges | 33 symlinks → `../../.claude/skills/<name>` |
| broken bridges | 0 |
| `.cursor/skills/speckit-*` | none |
| `.claude/skills/speckit-*` | none |
| `.specify` | absent |
| `specs/` | absent |
| `.cursor/rules` | 7 Debina rules only (no `specify-rules.mdc`) |

## Hook / MCP / CLI hashes (must stay unchanged)

| Path | sha256 |
|---|---|
| `.cursor/hooks.json` | `f84abc99b5d48f5353f6b84f9b40c53b426869776d96c9966572b2255db681f2` |
| `.cursor/cli.json` | `8aadc3308581a9bc9e72b62e068369bd16e0db567f167ce5d55342cb72779878` |
| `.cursor/mcp.json` | `248a0f98b56320a6180aef736aeb7ca9ef09871e9ca31f6711a3ecb569aae78a` |

## Canonical SKILL.md hashes (33)

```text
3d88ead01a4ac6eb1868c8e965aaa1d9be24f01f4679dd4c9c2eb617ebe9a78d  agent-instruction-hygiene
e08d3acb4b5c521ad6aacffeaea41609fdd74e56c9e24eb6b776b017e8d2d1d2  architecture-evolution-review
0147b2aa0bba4460caf6e9481ac3fba71c3cd8467bb4cbace6cf3deacdf734a5  artifact-derived-planning
a6a0b5b781d30eebbf22ff0b68459b899a78d8b7be1bffb5c475349f0f1d1f61  business-analyst
f959a303234c1f903eecf2342e9a4df5ee7e428ac797f55e90f7a770bd266b11  dagger-go-pipeline
89f0737ba6a7fb83957ef7011ee4b5fef0951534fcc1967b146b8ffbd8a175ef  debina-decision-record
db85e0982bf9b06370a70feed61712e4e3eb6f7c635e7571d45a904c7ea9b132  debina-discover-and-specify
193004d11e2bec8f33fb9f4bdd9d6d8c05f79abf018b71ffe51804c5259be63d  debina-implement-and-verify
7b28b1ce686e28046b69ebc6c9cff2381449d69a6004a62b80f605799c0bab8a  debina-iso20022-validation-lineage
24acf8c1059bb1fe36a47e394f2f57eefa1bc09226f2b640ac2d5cb7ce9ca491  debina-kafka-payment-contract
c4b2e4341159b7bc9c3ca1168a254f9d55469d3cc3c3d1543915e9772f934982  debina-money-movement-invariants
2166571688ff05db491c4808b877856837d7e76de1c3ee9df6a9a6e9e5614765  debina-payment-state-finality
d6c05329b692c6572fde26061e754d225cd6569a3aa056a0ecec7332c59e1b68  debina-postgres-transaction-coordination
ffed8dce004f4ce84ccc482500ba2ca3eaa7743a0ce290665b494a72c42d1156  debina-review-and-next-work
346173dfd2179603722c352443e0ac6c265dd1e723ba5d75c3d7aeb729e30026  debina-runtime-proof-testing
6c8173a02bf5751e1a01aface6062078da2f869089bdcf2c52e68688c2c4faec  enterprise-use-case-engineering
069c04679cf6d65dc55dd9b6c644873750aab95facf874551df3d4f78ecb993c  epic-story-task-catalog
02410f1f7ef4bb9d49fbd68710ff8d143a8dcd34b943dca09db7e51c06c323e8  impeccable
31431a6823b412c3cae606942480b27bf4aef4c4fe8e24bda6b0ad90c1544026  keycloak-realm-config
162b54fb70dabfa8b40c851562244573c65245ef2c93df124b85f03d543526a2  nextjs-bff-route
84b34d0642b815638d3dae67dc402a7b7f06c85cda2460ef0d86ffdd47acd033  planning-semantic-integrity
b0b14dc3994d98901de3d4dbb6175b40a272f842e7e5cd3da9fc71aa2479058b  postgres-rls-migration
efa604a2df9e2bdecc6f28571771620b66ea1643c9be36090194438533b28b42  product-manager
43f0d573c33838c7723fa0603a4fc0cc1c65a1dbb409cc88179e8857e8d201b0  scrum-master
b49ef2e316cbb987a3d2632937c78cec273a8d295978f34916640d364afbec64  sepa-nexus-database-review
c79ed4b6fa413d1d716c209ac34b868ad12c868d624eefcee9674c5836312956  sepa-nexus-database-testing
30559785dd8640a8381056076fbc28ae7557aa22d6a95e9b7383bfd52ad5545e  sepa-nexus-flyway-safe-change
8d255af1f1a0b23710d60458d1c7e9674852e8ff9205543cd93d669770966cdb  sepa-nexus-payments-data-integrity
ec5dabdc0ec518c13d724939987fdc52bde625a61887ba527c3ad92e7f32b9f6  session-handoff
05a9e25d4b3b6fc9afd8a4082ec0da7ee0b68bffc6546a262b8d0218d53b51fe  shadcn-component-scaffold
0262133530611f6deaeb50cd999274ce330b9c8f467f60639caee8446c11d6d9  source-backed-payments-modeling
4cc91e8fcd318c2ec7e4c1615f1024bc353a369a51272c99d5a3f85b571f8c78  spring-modulith-module
85677c80fb256ba423e6126b1c896642954665b351352ad2ccb7d6b3e1bb3146  technical-architect
```

## Bridge targets (all 33)

Each entry under `.cursor/skills/<name>` → `../../.claude/skills/<name>` (symlink).

## Pre-existing dirty tree (do not modify)

Unrelated prior work remains dirty (hooks, planning Wave 11 hygiene, agent wrappers, archives, CLAUDE.md access issues, etc.). This task must not restore or rewrite those paths.
