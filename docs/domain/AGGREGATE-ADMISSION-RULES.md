# Aggregate Admission Rules

Admit a new aggregate only with a written admission record that proves all relevant criteria: independent identity; independent lifecycle; independent commands; independent invariants; transactional consistency boundary; persistence requirement; and failure/recovery semantics. Record source authority, rail applicability, owner module, consistency boundary, alternatives rejected, and verification. A message envelope, file, batch, or projection may be an entity, value object, record, or technical artifact instead.

No admission record may merge modules, bypass one-writer-per-schema, weaken RLS, alter finality, or imply a new rail behaviour. Such changes require their own ADR.
