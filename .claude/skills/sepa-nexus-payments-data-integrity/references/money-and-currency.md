# Money and currency

## Storage

`numeric(precision, scale)` — never `real`/`double precision`/`float`. Floating-point binary representation cannot exactly represent most decimal fractions (`0.1 + 0.2 != 0.3` in IEEE 754), which is an unacceptable error source for money, however small it looks in a single operation — it compounds across aggregation, and any mismatch is a real, auditable discrepancy in a payments system, not a rounding curiosity.

```sql
amount numeric(18,2) NOT NULL,   -- confirm actual scale needed per source blueprint;
                                  -- some currencies (e.g. JPY) have 0 minor-unit digits,
                                  -- don't hardcode 2 without checking the currency in play
currency char(3) NOT NULL,       -- ISO 4217, always explicit, never inferred/defaulted
```

## Application-layer arithmetic

Java: `BigDecimal`, never `double`/`float`, for any amount calculation, comparison, or aggregation. `BigDecimal.equals()` is scale-sensitive (`new BigDecimal("1.0").equals(new BigDecimal("1.00"))` is `false`) — use `compareTo() == 0` for value equality, `equals()` only when scale must also match exactly (e.g. round-trip serialization checks).

## Currency is never assumed

A payment's currency is always carried explicitly alongside its amount at every layer — never inferred from tenant default, participant default, or "the currency of the last payment in this batch." If a source document doesn't state currency for a given field, that's a source gap to flag, not a default to invent.

## Rounding

Never round money silently mid-calculation. If a calculation (netting, fee apportionment, currency conversion) requires rounding, the rounding mode and the point at which it's applied must be explicit and match whatever the source blueprint specifies — don't introduce a rounding step the source doesn't call for, and don't let `BigDecimal`'s default `ArithmeticException`-on-inexact-division catch you by surprise in production; pick and state a `RoundingMode` explicitly for any division.

## Test obligation

Any new money-handling code needs at least one test asserting the *exact* decimal behavior at a boundary (e.g. a value that would misbehave under floating point, like repeated `0.1`-scale additions) — not just a round-number happy path that wouldn't have caught a float regression.
