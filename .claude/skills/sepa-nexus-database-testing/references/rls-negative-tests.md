# RLS negative tests

Real example: `backend/src/test/java/com/sepanexus/payment/PaymentsRlsTest.java`, `backend/src/test/java/com/sepanexus/modules/paymentlifecycle/service/BranchLevelRlsTest.java`, `backend/src/test/java/com/sepanexus/modules/paymentlifecycle/service/TenantGucIntegrationTest.java`.

Full policy-authoring rules live in the `postgres-rls-migration` skill — this file is specifically about the test side.

## The four required negative/edge cases

```java
@Test
void emptyTenantGucReturnsZeroRows() throws Exception {
    try (Connection connection = applicationConnection()) {
        // GUC never set for this connection
        assertEquals(0, paymentCount(connection));
    }
}

@Test
void crossTenantReadReturnsZeroRows() throws Exception {
    try (Connection connection = applicationConnection()) {
        setTenantGuc(connection, TENANT_A);
        assertEquals(0, countRowsForTenant(connection, TENANT_B));
    }
}

@Test
void invalidGucRaisesRatherThanExposingRows() throws Exception {
    try (Connection connection = applicationConnection()) {
        assertThrows(SQLException.class, () -> setTenantGuc(connection, "not-a-uuid"));
    }
}

@Test
void writeOutsideOwnTenantRejectedByWithCheck() throws Exception {
    try (Connection connection = applicationConnection()) {
        setTenantGuc(connection, TENANT_A);
        var ex = assertThrows(SQLException.class, () -> insertPayment(connection, TENANT_B));
        // WITH CHECK violation, not a generic failure
    }
}
```

## Pattern already established in this repo

`PaymentsRlsTest` seeds one row per tenant in `@BeforeEach` (truncate + reinsert, not accumulate across tests), then exercises the empty-GUC and cross-tenant cases against the `sepa_app` connection specifically — never the `test_admin`/superuser connection, which would bypass RLS entirely and prove nothing. Follow this exact shape (seed fixture → application-role connection → assert row count/rejection) for any new RLS-bearing table rather than inventing a different structure.

## Multi-level scoping (tenant + branch, etc.)

`BranchLevelRlsTest` shows the pattern for a second, nested scoping level beyond tenant (branch-level visibility within a tenant) — if a new table needs more than one GUC-driven scope, follow its shape (separate GUCs, separate test cases per scope boundary) rather than trying to encode multiple scopes into a single GUC value.
