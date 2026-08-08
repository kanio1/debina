# Skills layout baseline

## Identity

| Field | Value |
|---|---|
| branch | `rebase/enterprise-evolution` |
| HEAD | `196d40596c0049c03d20889e83e6539463381758` |
| health check | PASS |
| verify-fast baseline | FAIL — `CLAUDE.md` access; `HANDOFF-010`; Maven `/root/.m2` sandbox |

## Whole symlink

| Field | Value |
|---|---|
| whole symlink path | `.cursor/skills` |
| whole symlink target | `../.claude/skills` |
| resolved | `/home/suso/debina/.claude/skills` |
| canonical source | `.claude/skills` |

## Namespace collisions (pre-migration)

| Check | Result |
|---|---|
| `.cursor/skills/speckit-*` | none (path is whole-dir symlink into `.claude/skills`) |
| `.claude/skills/speckit-*` | none |
| frontmatter name mismatches | none (33/33 match directory names) |
| duplicate normalized names | none |

## Canonical Skills

**Count:** 33

All directories under `.claude/skills` contain `SKILL.md` with matching frontmatter `name`. Registry status treated as ACTIVE/on-disk eligible (no PLANNED-only entries without files).

| directory | SKILL.md hash (sha256) |
|---|---|
| agent-instruction-hygiene | `3d88ead01a4ac6eb1868c8e965aaa1d9be24f01f4679dd4c9c2eb617ebe9a78d` |
| architecture-evolution-review | `e08d3acb4b5c521ad6aacffeaea41609fdd74e56c9e24eb6b776b017e8d2d1d2` |
| artifact-derived-planning | `0147b2aa0bba4460caf6e9481ac3fba71c3cd8467bb4cbace6cf3deacdf734a5` |
| business-analyst | `a6a0b5b781d30eebbf22ff0b68459b899a78d8b7be1bffb5c475349f0f1d1f61` |
| dagger-go-pipeline | `f959a303234c1f903eecf2342e9a4df5ee7e428ac797f55e90f7a770bd266b11` |
| debina-decision-record | `89f0737ba6a7fb83957ef7011ee4b5fef0951534fcc1967b146b8ffbd8a175ef` |
| debina-discover-and-specify | `db85e0982bf9b06370a70feed61712e4e3eb6f7c635e7571d45a904c7ea9b132` |
| debina-implement-and-verify | `193004d11e2bec8f33fb9f4bdd9d6d8c05f79abf018b71ffe51804c5259be63d` |
| debina-iso20022-validation-lineage | `7b28b1ce686e28046b69ebc6c9cff2381449d69a6004a62b80f605799c0bab8a` |
| debina-kafka-payment-contract | `24acf8c1059bb1fe36a47e394f2f57eefa1bc09226f2b640ac2d5cb7ce9ca491` |
| debina-money-movement-invariants | `c4b2e4341159b7bc9c3ca1168a254f9d55469d3cc3c3d1543915e9772f934982` |
| debina-payment-state-finality | `2166571688ff05db491c4808b877856837d7e76de1c3ee9df6a9a6e9e5614765` |
| debina-postgres-transaction-coordination | `d6c05329b692c6572fde26061e754d225cd6569a3aa056a0ecec7332c59e1b68` |
| debina-review-and-next-work | `ffed8dce004f4ce84ccc482500ba2ca3eaa7743a0ce290665b494a72c42d1156` |
| debina-runtime-proof-testing | `346173dfd2179603722c352443e0ac6c265dd1e723ba5d75c3d7aeb729e30026` |
| enterprise-use-case-engineering | `6c8173a02bf5751e1a01aface6062078da2f869089bdcf2c52e68688c2c4faec` |
| epic-story-task-catalog | `069c04679cf6d65dc55dd9b6c644873750aab95facf874551df3d4f78ecb993c` |
| impeccable | `02410f1f7ef4bb9d49fbd68710ff8d143a8dcd34b943dca09db7e51c06c323e8` |
| keycloak-realm-config | `31431a6823b412c3cae606942480b27bf4aef4c4fe8e24bda6b0ad90c1544026` |
| nextjs-bff-route | `162b54fb70dabfa8b40c851562244573c65245ef2c93df124b85f03d543526a2` |
| planning-semantic-integrity | `84b34d0642b815638d3dae67dc402a7b7f06c85cda2460ef0d86ffdd47acd033` |
| postgres-rls-migration | `b0b14dc3994d98901de3d4dbb6175b40a272f842e7e5cd3da9fc71aa2479058b` |
| product-manager | `efa604a2df9e2bdecc6f28571771620b66ea1643c9be36090194438533b28b42` |
| scrum-master | `43f0d573c33838c7723fa0603a4fc0cc1c65a1dbb409cc88179e8857e8d201b0` |
| sepa-nexus-database-review | `b49ef2e316cbb987a3d2632937c78cec273a8d295978f34916640d364afbec64` |
| sepa-nexus-database-testing | `c79ed4b6fa413d1d716c209ac34b868ad12c868d624eefcee9674c5836312956` |
| sepa-nexus-flyway-safe-change | `30559785dd8640a8381056076fbc28ae7557aa22d6a95e9b7383bfd52ad5545e` |
| sepa-nexus-payments-data-integrity | `8d255af1f1a0b23710d60458d1c7e9674852e8ff9205543cd93d669770966cdb` |
| session-handoff | `ec5dabdc0ec518c13d724939987fdc52bde625a61887ba527c3ad92e7f32b9f6` |
| shadcn-component-scaffold | `05a9e25d4b3b6fc9afd8a4082ec0da7ee0b68bffc6546a262b8d0218d53b51fe` |
| source-backed-payments-modeling | `0262133530611f6deaeb50cd999274ce330b9c8f467f60639caee8446c11d6d9` |
| spring-modulith-module | `4cc91e8fcd318c2ec7e4c1615f1024bc353a369a51272c99d5a3f85b571f8c78` |
| technical-architect | `85677c80fb256ba423e6126b1c896642954665b351352ad2ccb7d6b3e1bb3146` |

## Skills skipped

None (no incomplete dirs, no `speckit-*`, no PLANNED-without-files).

## Git status baseline

Pre-existing dirty tree from prior tasks (hooks, Wave 11 planning, archives, etc.). Not attributed to this task. Not restored.
