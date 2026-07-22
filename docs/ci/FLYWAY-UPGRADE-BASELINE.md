# Flyway upgrade baseline

## Proposed, not yet executed

The candidate cutpoint is migration `V54__payment_approvals.sql`. It is meaningful because the Phase D ADR-N16 smoke exercises pending approval and then upgrades through the later audit and expiry migrations (`V55`–`V60`). It is not an arbitrary early schema snapshot.

After the Dagger runtime gate, verify an empty PostgreSQL 18 service by migrating to V54, validating, upgrading to latest and validating schema history, roles, expected schemas, RLS/grants and the pipeline-level probes. No schema dump is authoritative or will be created. The result remains `NOT_RUN`.
