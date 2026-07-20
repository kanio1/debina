# SECURITY DEFINER checklist

- Approval, narrow module-owned command, typed result, input/state/tenant validation.
- One physical Spring connection and `txid_current()` proof; no transaction statements in function.
- `NOLOGIN`, `NOSUPERUSER`, `NOBYPASSRLS` owner; executor has no direct DML or owner membership.
- Schema-qualified references; fixed search_path with no user-writable schema and `pg_temp` last.
- Revoke `PUBLIC`; grant only schema `USAGE` and exact function `EXECUTE`; review defaults and overloads.
