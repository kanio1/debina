package com.sepanexus.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Mutation guards for ADR-N11. Each assertion names a concrete weakening that must make this test
 * fail; runtime rollback, RLS and duplicate-effect mutants are exercised by GrossInstantOneTxFlowTest.
 */
@org.junit.jupiter.api.Tag("fast")
class GrossInstantMutationContractTest {

    private static final Path LEDGER = Path.of("src/main/resources/db/migration/ledger/V37__gross_instant_ordered_account_lock.sql");
    private static final Path SETTLEMENT_POST = Path.of("src/main/resources/db/migration/settlement/V39__gross_instant_finality_replay_bytea_fix.sql");
    private static final Path SETTLEMENT_REJECT = Path.of("src/main/resources/db/migration/settlement/V43__gross_instant_insufficient_liquidity_event_column_fix.sql");
    private static final Path PAYMENT = Path.of("src/main/resources/db/migration/payment/V40__gross_instant_payment_command_functions.sql");
    private static final Path ROLES = Path.of("src/main/resources/db/migration/V35__gross_instant_transaction_coordinator_roles.sql");
    private static final Path STRATEGY = Path.of("src/main/java/com/sepanexus/settlement/GrossInstantStrategy.java");
    private static final Path SETTLEMENT_ADAPTER = Path.of("src/main/java/com/sepanexus/settlement/JdbcGrossInstantSettlementCommandPort.java");
    private static final Path LEDGER_ADAPTER = Path.of("src/main/java/com/sepanexus/ledger/JdbcGrossInstantLedgerPort.java");
    private static final Path PAYMENT_ADAPTER = Path.of("src/main/java/com/sepanexus/modules/paymentlifecycle/service/JdbcGrossInstantPaymentFinalityPort.java");

    @Test
    void removingDeterministicRowLocksOrTheActiveStatePredicateIsDetected() throws Exception {
        String ledger = Files.readString(LEDGER);
        assertThat(ledger).contains("ORDER BY id FOR UPDATE");
        assertThat(ledger).contains("WHERE id = v_reservation.id AND state = 'ACTIVE'");
        assertThat(ledger).contains("USING ERRCODE = '40001'");
    }

    @Test
    void weakeningFinalityReplayEvidenceOrMakingItInvokerSecurityIsDetected() throws Exception {
        String settlement = Files.readString(SETTLEMENT_POST);
        assertThat(settlement).contains("SECURITY DEFINER VOLATILE PARALLEL UNSAFE");
        assertThat(settlement).contains("v_record.source_id <> p_post_entry_id OR v_record.evidence_hash <> p_evidence_hash");
        assertThat(settlement).contains("SET search_path = pg_catalog, settlement, reference_data, pg_temp");
        assertThat(settlement).contains("REVOKE ALL ON FUNCTION settlement.record_gross_instant_post");
    }

    @Test
    void removingPaymentDuplicateEventProtectionOrPublicRevokeIsDetected() throws Exception {
        String payment = Files.readString(PAYMENT);
        assertThat(payment).contains("payment_gross_instant_event_command_key");
        assertThat(payment).contains("payment_gross_instant_outbox_command_key");
        assertThat(payment).contains("ON CONFLICT DO NOTHING");
        assertThat(payment).contains("REVOKE ALL ON FUNCTION payment.project_gross_instant_finality");
        assertThat(payment).contains("SET search_path = pg_catalog, payment, pg_temp");
    }

    @Test
    void directExecutorGrantsOrGenericCrossSchemaExecutionAreDetected() throws Exception {
        String roles = Files.readString(ROLES);
        assertThat(roles).contains("gross_instant_executor_role LOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS");
        assertThat(roles).doesNotContain("GRANT INSERT ON ledger.");
        assertThat(roles).doesNotContain("GRANT UPDATE ON settlement.");
        assertThat(roles).doesNotContain("GRANT UPDATE ON payment.");
        assertThat(roles).doesNotContain("GRANT .* ON ALL TABLES");
    }

    @Test
    void rejectedAttemptFunctionRetainsTenantAndExpectedStateConflictChecks() throws Exception {
        String rejection = Files.readString(SETTLEMENT_REJECT);
        assertThat(rejection).contains("current_setting('app.tenant_id', true)");
        assertThat(rejection).contains("v_attempt.state <> 'REJECTED'");
        assertThat(rejection).contains("SECURITY DEFINER VOLATILE PARALLEL UNSAFE");
        assertThat(rejection).contains("SET search_path = pg_catalog, settlement, pg_temp");
    }

    @Test
    void adaptersCannotRegressToRawConnectionsCommitsOrSettlementDirectDomainDml() throws Exception {
        assertThat(Files.readString(STRATEGY)).contains("new TransactionTemplate(transactionManager)")
                .doesNotContain("DriverManager").doesNotContain("java.sql.Connection");
        assertThat(Files.readString(SETTLEMENT_ADAPTER)).contains("FROM settlement.record_gross_instant_post")
                .doesNotContain("INSERT INTO settlement.").doesNotContain("UPDATE settlement.")
                .doesNotContain("DELETE FROM settlement.").doesNotContain("ledger.").doesNotContain("payment.");
        for (Path adapter : java.util.List.of(LEDGER_ADAPTER, PAYMENT_ADAPTER)) {
            assertThat(Files.readString(adapter)).doesNotContain("DriverManager").doesNotContain(".commit(")
                    .doesNotContain(".rollback(");
        }
    }
}
