# ADR-N14 — Dedicated Keycloak database and offline realm backup

**Status:** Accepted (2026-07-21)
**Scope:** local development infrastructure only

## Context

The Keycloak security blueprints require a separate Keycloak database and a realm export. They do not prescribe the local Compose topology, backup artifact shape, or restore mechanics. H2 development storage cannot provide the required database backup boundary.

## Decision

Use a dedicated PostgreSQL 18 Compose service (`keycloak-postgres`) with its own database user, database, and persistent volume. `keycloak` connects only to that service. The payment PostgreSQL service remains separate.

`infra/scripts/backup-keycloak.sh <empty-directory>` stops only the Keycloak writer, creates a PostgreSQL custom-format dump, invokes Keycloak's supported offline `export` command in a short-lived Compose run container, copies the resulting realm files, and records SHA-256 checksums. If Keycloak was running, the script restarts it even after a failure.

## Options considered

| Option | Decision |
|---|---|
| Keep H2 and copy its files | Rejected: no dedicated database boundary and unsafe while running. |
| Share payment PostgreSQL with another database | Rejected: weaker operational isolation and conflates IAM with payment infrastructure. |
| Dedicated PostgreSQL service plus offline realm export | Accepted: least privilege, explicit failure behavior, and independently restorable artifacts. |

## Consequences and proof

Backups take a short Keycloak-only maintenance window; payment PostgreSQL and Kafka remain untouched. The dump is the authoritative recoverable state; the realm export is readable configuration evidence. This ADR is implemented and proven in the same Wave 6 slice by fresh Compose boot, backup, dump restore/readability, and realm JSON validation.
