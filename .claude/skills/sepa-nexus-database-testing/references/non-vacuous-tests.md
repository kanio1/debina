# Non-vacuous tests

A test that would pass even if the code under test were broken or absent has proven nothing. This is the single most common way an integration test suite gives false confidence — every test in this repo's database-testing surface should be checked against this before being considered done.

## The mutation-proof pattern

After a new test passes, deliberately break the thing it's supposed to verify (a controlled, temporary mutation), rerun, confirm the test now **fails**, then revert the mutation and confirm it passes again. If the test still passes against the broken code, it was vacuous — rewrite it before trusting it.

Concrete examples of the mutation to try, by test kind:

| Test kind | Mutation to try |
|---|---|
| Field extraction (e.g. `Pacs002IdentifierExtractionTest`) | Rename the source XML tag being extracted to something that doesn't exist; the test must now fail, not silently return null and pass |
| RLS cross-tenant negative | Temporarily comment out the `WHERE`/policy clause (or use a superuser connection instead of the app role); the "cross-tenant returns zero rows" test must now fail |
| Grant negative test | Temporarily grant the foreign role the privilege being tested against; the test must now fail |
| Append-only | Temporarily grant `UPDATE`/`DELETE` to the app role on the table; the rejection test must now fail |
| Idempotency | Temporarily remove the unique constraint on the idempotency scope; the concurrent-duplicate test must now show 2 rows, not 1 |

## Never leave the mutation in place

The mutation step is a **local, temporary, reverted** check — never a committed change. After confirming RED, revert immediately and confirm GREEN again before moving on. Check `git diff --check` and `grep` for any leftover mutation markers before considering the work done (see `sepa-nexus-flyway-safe-change`/EPIC-27 Story 27.1 implementation session for the exact same discipline applied to backend feature code, not just schema tests).

## Especially important for negative tests

A negative test (assert rejection/zero-rows/exception) is more prone to being vacuous than a positive test, because a broken assertion, a wrong exception type check, or an accidentally-superuser connection can all make the test pass "for the wrong reason" — it never actually exercised the boundary it claims to. Always mutation-check negative tests specifically, even under time pressure; they're exactly the tests protecting the properties (tenant isolation, writer isolation, append-only) this project treats as architecturally load-bearing.
