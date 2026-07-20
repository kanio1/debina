# Transaction-boundary inventory

Record for each command: caller, transaction owner, connection owner, current role, schemas/functions called, commit/rollback owner, idempotency key, durable writes before every failure point, retry/replay behavior, and observable partial state. Capture `txid_current()` at caller and inside each command function.
