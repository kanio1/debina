# Topic contract checklist

For every Kafka payment contract, record the authoritative §3.7 v2 row plus: business event name; physical topic; producer owner; consumer owner; payload schema; schema version; partition key; ordering scope; idempotency key; correlation identifiers; tenant identifier; occurred-at; published-at; trace identifier; PII/sensitive-data classification; retention assumption; retry policy; redelivery behavior; failure visibility; and compatibility policy.

State whether each field is `PROJECT-FREEZE`, source-backed, or `SOURCE-BLOCKED`. Do not fill a missing field with an inference. The topic catalog supplies ownership/key/ordering/DLQ/contract-owner baseline; payload evolution and operational detail require their own accepted evidence.
