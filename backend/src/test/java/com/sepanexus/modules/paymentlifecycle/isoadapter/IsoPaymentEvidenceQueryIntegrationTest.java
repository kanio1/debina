package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sepanexus.SepaNexusApplication;
import com.sepanexus.modules.PaymentIsoEvidenceQuery;
import com.sepanexus.modules.PaymentIsoEvidenceQuery.IsoIdentifierType;
import com.sepanexus.modules.paymentlifecycle.service.PaymentNotFoundException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** PostgreSQL 18 proof for the ISO-owned payment evidence port and its payment-visibility gate. */
@SpringBootTest(classes = SepaNexusApplication.class)
class IsoPaymentEvidenceQueryIntegrationTest {
    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID BRANCH_A = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID BRANCH_B = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");
    private static boolean initialized;

    @Autowired PaymentIsoEvidenceQuery evidenceQuery;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        initializeDatabase();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "sepa_app");
        registry.add("spring.datasource.password", () -> "dev-only-app");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "sepa_migration");
        registry.add("spring.flyway.password", () -> "dev-only-migration");
    }

    @BeforeEach
    void clearData() throws Exception {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE payment.payment_approvals, payment.payments, iso.payment_iso_identifiers, "
                    + "iso.message_lineage, iso.iso_messages CASCADE");
        }
    }

    @Test
    void readsJsonDirectAndPain001FactsWithoutFabricatingOptionalIdentifiers() throws Exception {
        UUID payment = payment(TENANT_A, BRANCH_A);
        Instant jsonAt = Instant.parse("2026-07-21T10:00:00Z");
        Instant painAt = Instant.parse("2026-07-21T10:01:00Z");
        UUID jsonMessage = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID painMessage = UUID.fromString("00000000-0000-0000-0000-000000000022");
        message(jsonMessage, "JSON_DIRECT", TENANT_A, jsonAt, null);
        identifier(payment, jsonMessage, "JSON_DIRECT", null, null, null, "E2E-JSON", null, null);
        lineage(payment, jsonMessage, jsonAt);
        version("pain.001", LocalDate.of(2025, 1, 1));
        message(painMessage, "pain.001", TENANT_A, painAt, painAt);
        identifier(payment, painMessage, "pain.001", "MSG-1", "PMT-1", null, "E2E-PAIN", null, null);
        lineage(payment, painMessage, painAt);

        PaymentIsoEvidenceQuery.PaymentIsoEvidence evidence = evidenceQuery.evidence(TENANT_A, BRANCH_A, payment);

        assertThat(evidence.messages()).extracting(PaymentIsoEvidenceQuery.IsoMessageEvidence::messageType)
                .containsExactly("JSON_DIRECT", "pain.001");
        assertThat(evidence.messages().get(0).messageVersion()).isNull();
        assertThat(evidence.messages().get(1).messageVersion()).isEqualTo("pain.001.001.09");
        assertThat(evidence.messages().get(0).lineageRole()).isEqualTo("ORIGINAL_INSTRUCTION");
        assertThat(evidence.messages().get(0).versionEffectiveFrom()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(evidence.messages().get(1).versionEffectiveFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(evidence.identifiers()).extracting(PaymentIsoEvidenceQuery.IsoIdentifierEvidence::type)
                .containsExactlyInAnyOrder(IsoIdentifierType.END_TO_END_ID, IsoIdentifierType.MSG_ID,
                        IsoIdentifierType.PMT_INF_ID, IsoIdentifierType.END_TO_END_ID);
        assertThat(evidence.identifiers()).extracting(PaymentIsoEvidenceQuery.IsoIdentifierEvidence::value)
                .containsExactlyInAnyOrder("E2E-JSON", "MSG-1", "PMT-1", "E2E-PAIN")
                .doesNotContain("TX-ID", "UETR");
    }

    @Test
    void equalTimestampsUseIsoMessageAndLineageIdentifiersAsStableTieBreakers() throws Exception {
        UUID payment = payment(TENANT_A, BRANCH_A);
        Instant same = Instant.parse("2026-07-21T10:00:00Z");
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000022");
        message(second, "JSON_DIRECT", TENANT_A, same, null); identifier(payment, second, "JSON_DIRECT", null, null, null, "E2E-2", null, null); lineage(payment, second, same);
        message(first, "JSON_DIRECT", TENANT_A, same, null); identifier(payment, first, "JSON_DIRECT", null, null, null, "E2E-1", null, null); lineage(payment, first, same);

        List<UUID> firstRead = evidenceQuery.evidence(TENANT_A, BRANCH_A, payment).messages().stream()
                .map(PaymentIsoEvidenceQuery.IsoMessageEvidence::isoMessageId).toList();
        List<UUID> secondRead = evidenceQuery.evidence(TENANT_A, BRANCH_A, payment).messages().stream()
                .map(PaymentIsoEvidenceQuery.IsoMessageEvidence::isoMessageId).toList();

        assertThat(firstRead).containsExactly(first, second);
        assertThat(secondRead).containsExactly(first, second);
    }

    @Test
    void foreignTenantAndForeignBranchAreDeniedBeforeIsoFactsCanBeRead() throws Exception {
        UUID payment = payment(TENANT_A, BRANCH_B);
        UUID message = UUID.randomUUID(); Instant at = Instant.parse("2026-07-21T10:00:00Z");
        message(message, "JSON_DIRECT", TENANT_A, at, null); identifier(payment, message, "JSON_DIRECT", null, null, null, "E2E", null, null); lineage(payment, message, at);

        assertThatThrownBy(() -> evidenceQuery.evidence(TENANT_B, BRANCH_B, payment))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> evidenceQuery.evidence(TENANT_A, BRANCH_A, payment))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> evidenceQuery.evidence(null, null, payment))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    private static UUID payment(UUID tenant, UUID branch) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection c = admin(); PreparedStatement s = c.prepareStatement("""
                INSERT INTO payment.payments (id, tenant_id, branch_id, amount, currency, debtor_iban, creditor_iban, status, created_at, version)
                VALUES (?, ?, ?, ?, 'EUR', 'DE89370400440532013000', 'FR7630006000011234567890189', 'RECEIVED', now(), 0)
                """)) {
            s.setObject(1, id); s.setObject(2, tenant); s.setObject(3, branch); s.setBigDecimal(4, new BigDecimal("10.00")); s.executeUpdate();
        }
        return id;
    }

    private static void version(String type, LocalDate effectiveFrom) throws Exception {
        try (Connection c = admin(); PreparedStatement s = c.prepareStatement("""
                INSERT INTO iso.iso_message_versions (message_type, effective_from) VALUES (?, ?) ON CONFLICT DO NOTHING
                """)) { s.setString(1, type); s.setObject(2, effectiveFrom); s.executeUpdate(); }
    }

    private static void message(UUID id, String type, UUID tenant, Instant created, Instant createdAtMessage) throws Exception {
        try (Connection c = admin(); PreparedStatement s = c.prepareStatement("""
                INSERT INTO iso.iso_messages (id, direction, message_type, parse_status, tenant_id, created_at, cre_dt_tm)
                VALUES (?, 'INBOUND', ?, 'PARSED', ?, ?, ?)
                """)) { s.setObject(1, id); s.setString(2, type); s.setObject(3, tenant); s.setTimestamp(4, java.sql.Timestamp.from(created)); s.setTimestamp(5, createdAtMessage == null ? null : java.sql.Timestamp.from(createdAtMessage)); s.executeUpdate(); }
    }

    private static void identifier(UUID payment, UUID message, String type, String msg, String pmt, String instr, String e2e, String tx, String uetr) throws Exception {
        try (Connection c = admin(); PreparedStatement s = c.prepareStatement("""
                INSERT INTO iso.payment_iso_identifiers (payment_id, source_message_type, iso_message_id, msg_id, pmt_inf_id, instr_id, end_to_end_id, tx_id, uetr)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) { s.setObject(1, payment); s.setString(2, type); s.setObject(3, message); s.setString(4, msg); s.setString(5, pmt); s.setString(6, instr); s.setString(7, e2e); s.setString(8, tx); s.setString(9, uetr); s.executeUpdate(); }
    }

    private static void lineage(UUID payment, UUID message, Instant created) throws Exception {
        try (Connection c = admin(); PreparedStatement s = c.prepareStatement("""
                INSERT INTO iso.message_lineage (lineage_role, iso_message_id, payment_id, created_at)
                VALUES ('ORIGINAL_INSTRUCTION', ?, ?, ?)
                """)) { s.setObject(1, message); s.setObject(2, payment); s.setTimestamp(3, java.sql.Timestamp.from(created)); s.executeUpdate(); }
    }

    private static synchronized void initializeDatabase() {
        if (initialized) return;
        POSTGRES.start();
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        } catch (Exception exception) { throw new IllegalStateException("Cannot initialize PostgreSQL test container", exception); }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        initialized = true;
    }

    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin"); }
}
