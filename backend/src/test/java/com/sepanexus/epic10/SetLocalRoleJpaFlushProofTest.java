package com.sepanexus.epic10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * EPIC-10 transaction-coordination decision gate — companion to {@link SetLocalRoleSqlProofTest}.
 * That test proves {@code SET LOCAL ROLE} preserves one Postgres transaction/txid across role
 * switches; this one proves a hazard the SQL-only proof cannot show: real JPA/Hibernate write-behind
 * (already discovered once this session as a plain FK bug — {@code V19}'s migration javadoc, "JPA
 * {@code paymentRepository.save()} doesn't flush before the subsequent raw JdbcTemplate insert in
 * the same transaction") interacts dangerously with mid-transaction role switching specifically: a
 * deferred INSERT does not execute under the role active when {@code save()} was called, it executes
 * under WHATEVER role is active when Hibernate actually flushes — by default, at commit. If a later
 * stage in the same transaction has since switched to a narrower role that lacks the grant, the
 * flush fails there, silently far from the line that "caused" it. {@code PaymentEntity} uses
 * {@code GenerationType.UUID} (an in-memory, application-side generator — confirmed by existing
 * runtime code that reads {@code payment.getId()} immediately after {@code save()}, before any
 * flush), so this hazard is real and reproducible without needing a DB-generated PK.
 *
 * <p>Reuses the real {@code payment.payments} table/entity/repository (via Flyway on Testcontainers,
 * never against the long-running dev database) rather than a synthetic table, because the hazard is
 * specifically about Hibernate's write-behind on a real JPA entity — a hand-rolled proof entity would
 * risk not reproducing genuine Hibernate flush timing.
 */
@SpringBootTest(classes = SepaNexusApplication.class)
class SetLocalRoleJpaFlushProofTest {

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
        registry.add("spring.datasource.username", () -> "proof_runtime_login");
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
            statement.execute("CREATE ROLE proof_runtime_login LOGIN NOINHERIT PASSWORD 'proof'");
            statement.execute("CREATE ROLE proof_payment_role NOLOGIN");
            statement.execute("CREATE ROLE proof_iso_role NOLOGIN");
            statement.execute("GRANT proof_payment_role TO proof_runtime_login");
            statement.execute("GRANT proof_iso_role TO proof_runtime_login");

            // proof_payment_role gets exactly the grant sepa_app has on payment.payments today —
            // modelling the future payment-lifecycle-owned role from Story 10.1.
            statement.execute("GRANT USAGE ON SCHEMA payment TO proof_payment_role");
            statement.execute("GRANT SELECT, INSERT, UPDATE ON payment.payments TO proof_payment_role");

            // proof_iso_role models the future iso-adapter-owned role: it can write ITS OWN schema,
            // never payment.payments.
            statement.execute("CREATE SCHEMA proof_iso");
            statement.execute("CREATE TABLE proof_iso.lineage_tbl (id uuid PRIMARY KEY, payment_id uuid NOT NULL, val text)");
            statement.execute("GRANT USAGE ON SCHEMA proof_iso TO proof_iso_role");
            statement.execute("GRANT SELECT, INSERT ON proof_iso.lineage_tbl TO proof_iso_role");
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void deferredJpaFlushExecutesUnderWhicheverRoleIsActiveAtCommitTime_notTheRoleActiveAtSaveTime() {
        UUID tenantId = UUID.randomUUID();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            setLocalRole("proof_payment_role");
            setTenantGuc(tenantId);

            PaymentEntity payment = PaymentEntity.received(tenantId, null, new BigDecimal("10.00"), "EUR",
                    "DE89370400440532013000", "FR7630006000011234567890189", Instant.now());
            paymentRepository.save(payment);
            // No explicit flush: GenerationType.UUID assigns the id in memory, so this compiles and
            // "works" with no visible error yet — the INSERT is still only queued in Hibernate's
            // action queue.
            assertThat(payment.getId()).isNotNull();

            // A later stage in the SAME transaction switches to the iso-adapter-equivalent role —
            // exactly what Story 10.1 would require payment-lifecycle code to do (or an equivalent
            // mechanism) before touching iso.* tables.
            setLocalRole("proof_iso_role");
            insertLineageRow(payment.getId());

            // Method returns normally here -> TransactionTemplate commits -> JPA flushes the queued
            // payment.payments INSERT NOW, under whatever role SET LOCAL last left active:
            // proof_iso_role, which has no grant on payment.payments at all.
        })).hasRootCauseInstanceOf(java.sql.SQLException.class)
                .rootCause()
                .hasMessageContaining("permission denied for schema payment");

        assertThat(countPaymentRows(tenantId)).isZero();
    }

    @Test
    void explicitFlushBeforeTheRoleSwitchAvoidsTheHazard() {
        UUID tenantId = UUID.randomUUID();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        UUID paymentId = transactionTemplate.execute(status -> {
            setLocalRole("proof_payment_role");
            setTenantGuc(tenantId);

            PaymentEntity payment = PaymentEntity.received(tenantId, null, new BigDecimal("10.00"), "EUR",
                    "DE89370400440532013000", "FR7630006000011234567890189", Instant.now());
            paymentRepository.save(payment);
            entityManager.flush(); // forces the INSERT to execute now, while proof_payment_role is active

            setLocalRole("proof_iso_role");
            insertLineageRow(payment.getId());

            return payment.getId();
        });

        assertThat(paymentId).isNotNull();
        assertThat(countPaymentRows(tenantId)).isEqualTo(1);
    }

    private void setLocalRole(String role) {
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL ROLE " + role);
            }
        });
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

    private void insertLineageRow(UUID paymentId) {
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO proof_iso.lineage_tbl (id, payment_id, val) VALUES (?, ?, 'proof')")) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, paymentId);
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

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
