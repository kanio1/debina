package com.sepanexus.epic10;

import static org.assertj.core.api.Assertions.assertThat;

import com.sepanexus.SepaNexusApplication;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * EPIC-10 transaction-coordination decision gate — the direct counterpart to
 * {@link SetLocalRoleJpaFlushProofTest}, same real {@code PaymentEntity}/{@code PaymentRepository}
 * setup, but calling a {@code SECURITY DEFINER} function instead of switching the connection's own
 * role. The hypothesis this proves or disproves: because the caller's {@code current_user} never
 * actually changes for the SECURITY DEFINER's duration (proven separately in
 * {@link SecurityDefinerTransactionProofTest#sessionUserAndCurrentUserRestoredAfterFunctionReturns}),
 * Hibernate's deferred flush at commit time should execute under the caller's own role regardless of
 * whether a {@code SECURITY DEFINER} call happened in between — unlike {@code SET LOCAL ROLE}, where
 * the deferred flush picks up whatever role was LAST active. If true, no explicit
 * {@code entityManager.flush()} should be required around the function call purely for privilege
 * reasons (only for the usual, unrelated reasons — e.g. needing the row to exist for a later query in
 * the same transaction).
 */
@SpringBootTest(classes = SepaNexusApplication.class)
class SecurityDefinerJpaFlushProofTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");
    private static boolean initialized;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        initializeDatabase();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "proof_payment_role");
        registry.add("spring.datasource.password", () -> "proof");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "sepa_migration");
        registry.add("spring.flyway.password", () -> "dev-only-migration");
    }

    private static synchronized void initializeDatabase() {
        if (initialized) {
            return;
        }
        initialized = true;
        POSTGRES.start();
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE proof_payment_role LOGIN NOINHERIT PASSWORD 'proof'");
            statement.execute("CREATE ROLE proof_iso_role NOLOGIN");

            // proof_payment_role gets exactly the grant sepa_app has on payment.payments today —
            // modelling the future payment-lifecycle-owned role from Story 10.1. NOINHERIT means it
            // starts with none of proof_iso_role's privileges — the only door into proof_iso is the
            // narrow SECURITY DEFINER function below.
            statement.execute("GRANT USAGE ON SCHEMA payment TO proof_payment_role");
            statement.execute("GRANT SELECT, INSERT, UPDATE ON payment.payments TO proof_payment_role");

            statement.execute("CREATE SCHEMA proof_iso");
            statement.execute("CREATE TABLE proof_iso.lineage_tbl (id uuid PRIMARY KEY, payment_id uuid NOT NULL, val text NOT NULL)");
            statement.execute("GRANT USAGE ON SCHEMA proof_iso TO proof_iso_role");
            statement.execute("GRANT SELECT, INSERT ON proof_iso.lineage_tbl TO proof_iso_role");
            statement.execute("GRANT USAGE ON SCHEMA proof_iso TO proof_payment_role");

            statement.execute("""
                    CREATE FUNCTION proof_iso.record_original_instruction(p_payment_id uuid, p_val text)
                    RETURNS uuid
                    LANGUAGE sql
                    SECURITY DEFINER
                    SET search_path = proof_iso, pg_temp
                    BEGIN ATOMIC
                        INSERT INTO proof_iso.lineage_tbl (id, payment_id, val)
                        VALUES (gen_random_uuid(), p_payment_id, p_val)
                        RETURNING id;
                    END
                    """);
            statement.execute("ALTER FUNCTION proof_iso.record_original_instruction(uuid, text) OWNER TO proof_iso_role");
            statement.execute("REVOKE ALL ON FUNCTION proof_iso.record_original_instruction(uuid, text) FROM PUBLIC");
            statement.execute("GRANT EXECUTE ON FUNCTION proof_iso.record_original_instruction(uuid, text) TO proof_payment_role");
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void deferredJpaFlushSucceedsAtCommitTimeWithNoExplicitFlushAroundTheFunctionCall() {
        UUID tenantId = UUID.randomUUID();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        UUID paymentId = transactionTemplate.execute(status -> {
            setTenantGuc(tenantId);

            PaymentEntity payment = PaymentEntity.received(tenantId, null, new BigDecimal("10.00"), "EUR",
                    "DE89370400440532013000", "FR7630006000011234567890189", Instant.now());
            paymentRepository.save(payment);
            // Deliberately NO entityManager.flush() here — this is the exact sequence that failed in
            // SetLocalRoleJpaFlushProofTest. Unlike SET LOCAL ROLE, the caller's own current_user
            // never actually changes for a SECURITY DEFINER call, so the deferred INSERT below
            // (executed at commit) should still run under proof_payment_role, which has the grant.
            callFunction(payment.getId(), "no-explicit-flush");

            return payment.getId();
        });

        assertThat(paymentId).isNotNull();
        assertThat(countPaymentRows(tenantId)).isEqualTo(1);
        assertThat(countLineageRows(paymentId)).isEqualTo(1);
    }

    @Test
    void explicitFlushBeforeTheFunctionCallAlsoSucceeds() {
        UUID tenantId = UUID.randomUUID();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        UUID paymentId = transactionTemplate.execute(status -> {
            setTenantGuc(tenantId);

            PaymentEntity payment = PaymentEntity.received(tenantId, null, new BigDecimal("10.00"), "EUR",
                    "DE89370400440532013000", "FR7630006000011234567890189", Instant.now());
            paymentRepository.save(payment);
            entityManager.flush();

            callFunction(payment.getId(), "explicit-flush");

            return payment.getId();
        });

        assertThat(paymentId).isNotNull();
        assertThat(countPaymentRows(tenantId)).isEqualTo(1);
        assertThat(countLineageRows(paymentId)).isEqualTo(1);
    }

    private void setTenantGuc(UUID tenantId) {
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT set_config('app.tenant_id', ?, true)")) {
                statement.setString(1, tenantId.toString());
                statement.execute();
            }
        });
    }

    private void callFunction(UUID paymentId, String val) {
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT proof_iso.record_original_instruction(?, ?)")) {
                statement.setObject(1, paymentId);
                statement.setString(2, val);
                statement.execute();
            }
        });
    }

    private static int countPaymentRows(UUID tenantId) {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM payment.payments WHERE tenant_id = ?")) {
            statement.setObject(1, tenantId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int countLineageRows(UUID paymentId) {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM proof_iso.lineage_tbl WHERE payment_id = ?")) {
            statement.setObject(1, paymentId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
