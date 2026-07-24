package com.sepanexus.modules.paymentlifecycle.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sepanexus.SepaNexusApplication;
import com.sepanexus.signature.KeyPurpose;
import com.sepanexus.signature.KeyRegistryPort;
import com.sepanexus.signature.SignatureKeyRegistration;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * EPIC-19 Story 19.4 — end-to-end proof of {@code POST /api/v1/iso/pain001} over the full pipeline
 * ({@code archive → verify → hardened parse → canonical map → domain idempotency → create payment →
 * ISO identifiers/lineage → outbox}), real Postgres + real Ed25519 signing throughout (same
 * discipline as {@code SignatureBeforeParseOrderingTest}/{@code JsonDirectIngestionTest} — nothing
 * about the pipeline itself is mocked, only the JWT is a test principal).
 */
@SpringBootTest(classes = SepaNexusApplication.class)
@AutoConfigureMockMvc
@org.junit.jupiter.api.Tag("testcontainers")
class Pain001SubmissionEndpointTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");
    private static boolean initialized;
    private static KeyPair signingKeyPair;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeyRegistryPort keyRegistryPort;

    private final UUID participantId = UUID.randomUUID();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        initializeDatabase();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "sepa_app");
        registry.add("spring.datasource.password", () -> "dev-only-app");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "sepa_migration");
        registry.add("spring.flyway.password", () -> "dev-only-migration");
        registry.add("signature.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("signature.datasource.username", () -> "signature_role");
        registry.add("signature.datasource.password", () -> "dev-only-signature");
    }

    @BeforeAll
    static void generateKeyPair() throws Exception {
        signingKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE payment.payment_approvals, payment.payments, payment.outbox_events, "
                    + "payment.payment_status_history, payment.payment_events, "
                    + "ingress.idempotency_keys, ingress.raw_inbound_messages, "
                    + "iso.payment_iso_identifiers, iso.message_lineage, iso.iso_messages, "
                    + "iso.iso_message_parse_errors, reference_data.approval_matrix_rules, "
                    + "signature.signature_keys, signature.message_signatures, signature.signature_verification_events "
                    + "CASCADE");
        }
        keyRegistryPort.register(new SignatureKeyRegistration(participantId, KeyPurpose.VERIFY, "Ed25519",
                Base64.getEncoder().encodeToString(signingKeyPair.getPublic().getEncoded()), "synthetic-lab-ref",
                Instant.now().minus(1, ChronoUnit.HOURS), null));
    }

    @Test
    void validSignedPain001CreatesPaymentWithIdentifiersLineageAndOutbox() throws Exception {
        byte[] xml = pain001("MSG-HAPPY", "PMTINF-HAPPY", "E2E-HAPPY", "100.00", "EUR").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(pain001Request(UUID.randomUUID(), xml, sign(xml), UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));

        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-HAPPY'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-HAPPY' "
                + "AND source_message_type = 'pain.001' AND msg_id = 'MSG-HAPPY' AND pmt_inf_id = 'PMTINF-HAPPY'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM iso.message_lineage ml "
                + "JOIN iso.payment_iso_identifiers pii ON pii.payment_id = ml.payment_id "
                + "WHERE pii.end_to_end_id = 'E2E-HAPPY' AND ml.lineage_role = 'ORIGINAL_INSTRUCTION'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.outbox_events oe "
                + "JOIN iso.payment_iso_identifiers pii ON pii.payment_id = oe.aggregate_id "
                + "WHERE pii.end_to_end_id = 'E2E-HAPPY'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM signature.signature_verification_events WHERE verdict = 'VERIFIED'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM iso.iso_message_parse_errors")).isZero();
        assertThat(count("SELECT count(*) FROM iso.iso_messages im "
                + "JOIN iso.payment_iso_identifiers pii ON pii.iso_message_id = im.id "
                + "WHERE pii.end_to_end_id = 'E2E-HAPPY' "
                + "AND im.source_message_created_at = '2026-07-15 10:00:00+00' "
                + "AND im.recorded_at IS NOT NULL "
                + "AND im.cre_dt_tm IS NULL "
                + "AND im.recorded_at <> im.source_message_created_at")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.payments p "
                + "JOIN iso.payment_iso_identifiers pii ON pii.payment_id = p.id "
                + "WHERE pii.end_to_end_id = 'E2E-HAPPY' AND p.amount = 100.00 AND p.currency = 'EUR' "
                + "AND p.debtor_iban = 'DE89370400440532013000' AND p.creditor_iban = 'FR7630006000011234567890189'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_schema IN ('payment','iso') AND column_name IN ('pmt_mtd','ctrl_sum','nb_of_txs')"))
                .as("VALIDATE_ONLY fields must not be persisted as domain columns")
                .isZero();
        assertThat(count("SELECT count(*) FROM payment.payment_status_history h "
                + "JOIN iso.payment_iso_identifiers pii ON pii.payment_id = h.payment_id "
                + "WHERE pii.end_to_end_id = 'E2E-HAPPY' AND h.seq = 1 AND h.from_status IS NULL "
                + "AND h.to_status = 'RECEIVED' AND h.event_ref IS NOT NULL")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.payment_events e "
                + "JOIN payment.payment_status_history h ON h.event_ref = e.id "
                + "JOIN iso.payment_iso_identifiers pii ON pii.payment_id = h.payment_id "
                + "WHERE pii.end_to_end_id = 'E2E-HAPPY'")).isEqualTo(1);
    }

    @Test
    void malformedSignatureRejectsWithoutCreatingPayment() throws Exception {
        byte[] xml = pain001("MSG-MALFORMED", "PMTINF-MALFORMED", "E2E-MALFORMED", "10.00", "EUR")
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(pain001Request(UUID.randomUUID(), xml, new byte[] {1, 2, 3}, UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SIGNATURE_FAILED"))
                .andExpect(jsonPath("$.profileOutcome").value("MALFORMED_SIGNATURE"));

        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-MALFORMED'")).isZero();
        assertThat(count("SELECT count(*) FROM iso.iso_message_parse_errors")).isZero();
        assertThat(count("SELECT count(*) FROM signature.signature_verification_events WHERE verdict = 'FAILED'"))
                .isEqualTo(1);
    }

    @Test
    void unknownSignerRejectsWithoutCreatingPayment() throws Exception {
        byte[] xml = pain001("MSG-UNKNOWN", "PMTINF-UNKNOWN", "E2E-UNKNOWN", "10.00", "EUR")
                .getBytes(StandardCharsets.UTF_8);
        UUID unregisteredSigner = UUID.randomUUID();

        mockMvc.perform(pain001Request(UUID.randomUUID(), xml, sign(xml), UUID.randomUUID().toString(),
                        unregisteredSigner))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SIGNATURE_FAILED"))
                .andExpect(jsonPath("$.profileOutcome").value("UNKNOWN_SIGNER"));

        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-UNKNOWN'")).isZero();
        assertThat(count("SELECT count(*) FROM iso.iso_message_parse_errors")).isZero();
    }

    @Test
    void tamperedSignatureRejectsWithoutCreatingPaymentButArchivesEvidence() throws Exception {
        byte[] xml = pain001("MSG-TAMPER", "PMTINF-TAMPER", "E2E-TAMPER", "10.00", "EUR").getBytes(StandardCharsets.UTF_8);
        byte[] signature = sign(xml);
        signature[0] = (byte) (signature[0] + 1);

        mockMvc.perform(pain001Request(UUID.randomUUID(), xml, signature, UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SIGNATURE_FAILED"))
                .andExpect(jsonPath("$.profileOutcome").value("INVALID_SIGNATURE"));

        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-TAMPER'")).isZero();
        assertThat(count("SELECT count(*) FROM ingress.raw_inbound_messages WHERE message_type = 'pain.001'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM signature.signature_verification_events WHERE verdict = 'FAILED'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM iso.iso_message_parse_errors"))
                .as("parser never ran — signature failed first, this is signature evidence, not a parse error")
                .isZero();
    }

    @Test
    void missingRequiredSignatureRejectsWithoutCreatingPayment() throws Exception {
        byte[] xml = pain001("MSG-NOSIG", "PMTINF-NOSIG", "E2E-NOSIG", "10.00", "EUR").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(pain001Request(UUID.randomUUID(), xml, null, UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SIGNATURE_FAILED"))
                .andExpect(jsonPath("$.profileOutcome").value("MISSING_SIGNATURE"));

        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-NOSIG'")).isZero();
        assertThat(count("SELECT count(*) FROM iso.iso_message_parse_errors")).isZero();
    }

    @Test
    void xxePayloadRejectedAsMalformedXmlWithoutCreatingPayment() throws Exception {
        byte[] xml = """
                <?xml version="1.0"?>
                <!DOCTYPE Document [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">&xxe;</Document>
                """.getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(pain001Request(UUID.randomUUID(), xml, sign(xml), UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_XML"));

        assertThat(count("SELECT count(*) FROM payment.payments")).isZero();
        assertThat(count("SELECT count(*) FROM iso.iso_message_parse_errors "
                + "WHERE error_code = 'MALFORMED_XML' AND message_type_guess = 'pain.001' "
                + "AND raw_message_id IS NOT NULL AND error_message IS NOT NULL")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM iso.iso_message_parse_errors ime "
                + "JOIN ingress.raw_inbound_messages rim ON rim.id = ime.raw_message_id")).isEqualTo(1);
    }

    @Test
    void entityExpansionPayloadRejectedAsMalformedXmlWithoutCreatingPayment() throws Exception {
        byte[] xml = """
                <?xml version="1.0"?>
                <!DOCTYPE lolz [
                  <!ENTITY lol "lol">
                  <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
                  <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
                ]>
                <lolz>&lol3;</lolz>
                """.getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(pain001Request(UUID.randomUUID(), xml, sign(xml), UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_XML"));

        assertThat(count("SELECT count(*) FROM payment.payments")).isZero();
        assertThat(count("SELECT count(*) FROM iso.iso_message_parse_errors WHERE error_code = 'MALFORMED_XML'"))
                .isEqualTo(1);
    }

    @Test
    void unsupportedPain001VersionRejectedWithoutCreatingPaymentOrParseError() throws Exception {
        byte[] xml = pain001("MSG-VER", "PMTINF-VER", "E2E-VER", "10.00", "EUR")
                .replace("pain.001.001.09", "pain.001.001.03").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(pain001Request(UUID.randomUUID(), xml, sign(xml), UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MESSAGE_VERSION"));

        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-VER'")).isZero();
        assertThat(count("SELECT count(*) FROM iso.iso_message_parse_errors"))
                .as("the XML parsed fine — this is a canonical mapping rejection, not a parser/hardening failure")
                .isZero();
    }

    @Test
    void missingRequiredCanonicalFieldRejectedWithoutCreatingPaymentOrParseError() throws Exception {
        byte[] xml = pain001("MSG-MISS", "PMTINF-MISS", "", "10.00", "EUR").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(pain001Request(UUID.randomUUID(), xml, sign(xml), UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("MISSING_REQUIRED_ELEMENT"));

        assertThat(count("SELECT count(*) FROM payment.payments")).isZero();
        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers")).isZero();
        assertThat(count("SELECT count(*) FROM iso.message_lineage")).isZero();
        assertThat(count("SELECT count(*) FROM payment.outbox_events")).isZero();
        assertThat(count("SELECT count(*) FROM iso.iso_message_parse_errors"))
                .as("the XML parsed fine — this is a canonical mapping rejection, not a parser/hardening failure")
                .isZero();
    }

    @Test
    void repeatedSubmissionWithSameIdempotencyKeyReplaysSamePayment() throws Exception {
        byte[] xml = pain001("MSG-REPLAY", "PMTINF-REPLAY", "E2E-REPLAY", "10.00", "EUR").getBytes(StandardCharsets.UTF_8);
        String idempotencyKey = UUID.randomUUID().toString();
        UUID tenantId = UUID.randomUUID();

        String firstLocation = mockMvc.perform(pain001Request(tenantId, xml, sign(xml), idempotencyKey))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String secondLocation = mockMvc.perform(pain001Request(tenantId, xml, sign(xml), idempotencyKey))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        assertThat(secondLocation).isEqualTo(firstLocation);
        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-REPLAY'")).isEqualTo(1);
    }

    /**
     * EPIC-21 Story 21.2: {@code EndToEndId} is an ISO lineage identifier, not a payment
     * uniqueness key. Two genuinely distinct pain.001 submissions (different {@code
     * Idempotency-Key}) that happen to reuse the same business {@code EndToEndId} are two distinct
     * payments, each with its own {@code iso_message_id}/lineage/outbox row.
     */
    @Test
    void twoDifferentIdempotencyKeysWithSameEndToEndIdCreateTwoPayments() throws Exception {
        UUID tenantId = UUID.randomUUID();
        byte[] first = pain001("MSG-REUSE-1", "PMTINF-REUSE-1", "E2E-REUSED", "10.00", "EUR")
                .getBytes(StandardCharsets.UTF_8);
        byte[] second = pain001("MSG-REUSE-2", "PMTINF-REUSE-2", "E2E-REUSED", "20.00", "EUR")
                .getBytes(StandardCharsets.UTF_8);

        String firstLocation = mockMvc.perform(pain001Request(tenantId, first, sign(first), UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String secondLocation = mockMvc.perform(pain001Request(tenantId, second, sign(second), UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        assertThat(secondLocation).isNotEqualTo(firstLocation);
        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-REUSED'"))
                .isEqualTo(2);
        assertThat(count("SELECT count(DISTINCT iso_message_id) FROM iso.payment_iso_identifiers "
                + "WHERE end_to_end_id = 'E2E-REUSED'")).as("each payment gets its own iso_message_id").isEqualTo(2);
        assertThat(count("SELECT count(DISTINCT ml.payment_id) FROM iso.message_lineage ml "
                + "JOIN iso.payment_iso_identifiers pii ON pii.iso_message_id = ml.iso_message_id "
                + "WHERE pii.end_to_end_id = 'E2E-REUSED' AND ml.lineage_role = 'ORIGINAL_INSTRUCTION'"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM payment.outbox_events oe "
                + "JOIN iso.payment_iso_identifiers pii ON pii.payment_id = oe.aggregate_id "
                + "WHERE pii.end_to_end_id = 'E2E-REUSED'")).isEqualTo(2);
    }

    @Test
    void sameIdempotencyKeyDifferentBodyIsConflict() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        UUID tenantId = UUID.randomUUID();
        byte[] first = pain001("MSG-CONFLICT-1", "PMTINF-CONFLICT", "E2E-CONFLICT-1", "10.00", "EUR")
                .getBytes(StandardCharsets.UTF_8);
        byte[] second = pain001("MSG-CONFLICT-2", "PMTINF-CONFLICT", "E2E-CONFLICT-2", "20.00", "EUR")
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(pain001Request(tenantId, first, sign(first), idempotencyKey)).andExpect(status().isCreated());
        mockMvc.perform(pain001Request(tenantId, second, sign(second), idempotencyKey)).andExpect(status().isConflict());

        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-CONFLICT-2'")).isZero();
    }

    @Test
    void sameIdempotencyKeyDifferentTenantsRemainIsolated() throws Exception {
        String idempotencyKey = "shared-key-" + UUID.randomUUID();
        UUID tenantOne = UUID.randomUUID();
        UUID tenantTwo = UUID.randomUUID();
        byte[] first = pain001("MSG-TENANT-1", "PMTINF-TENANT-1", "E2E-TENANT-1", "10.00", "EUR")
                .getBytes(StandardCharsets.UTF_8);
        byte[] second = pain001("MSG-TENANT-2", "PMTINF-TENANT-2", "E2E-TENANT-2", "20.00", "EUR")
                .getBytes(StandardCharsets.UTF_8);

        String firstLocation = mockMvc.perform(pain001Request(tenantOne, first, sign(first), idempotencyKey))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String secondLocation = mockMvc.perform(pain001Request(tenantTwo, second, sign(second), idempotencyKey))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        assertThat(secondLocation).isNotEqualTo(firstLocation);
        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-TENANT-1'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-TENANT-2'"))
                .isEqualTo(1);
    }

    @Test
    void concurrentSameIdempotencyKeySubmissionsCreateOnePayment() throws Exception {
        byte[] xml = pain001("MSG-CONCURRENT", "PMTINF-CONCURRENT", "E2E-CONCURRENT", "10.00", "EUR")
                .getBytes(StandardCharsets.UTF_8);
        String idempotencyKey = UUID.randomUUID().toString();
        UUID tenantId = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> submit = () -> {
            start.await();
            return mockMvc.perform(pain001Request(tenantId, xml, sign(xml), idempotencyKey))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getHeader("Location");
        };
        try {
            Future<String> first = executor.submit(submit);
            Future<String> second = executor.submit(submit);
            start.countDown();
            assertThat(first.get()).isEqualTo(second.get());
            assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-CONCURRENT'"))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedSignatureDoesNotConsumeIdempotencyKey() throws Exception {
        byte[] xml = pain001("MSG-RETRY", "PMTINF-RETRY", "E2E-RETRY", "10.00", "EUR")
                .getBytes(StandardCharsets.UTF_8);
        String idempotencyKey = UUID.randomUUID().toString();
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(pain001Request(tenantId, xml, null, idempotencyKey))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SIGNATURE_FAILED"));

        assertThat(count("SELECT count(*) FROM ingress.idempotency_keys WHERE idem_key = '" + idempotencyKey + "'"))
                .isZero();

        mockMvc.perform(pain001Request(tenantId, xml, sign(xml), idempotencyKey))
                .andExpect(status().isCreated());

        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-RETRY'")).isEqualTo(1);
    }

    @Test
    void mappingFailureDoesNotConsumeIdempotencyKey() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        UUID tenantId = UUID.randomUUID();
        byte[] invalid = pain001("MSG-MAP-FAIL", "PMTINF-MAP-FAIL", "E2E-MAP-FAIL", "10.00", "EUR")
                .replace("<CtrlSum>10.00</CtrlSum>", "<CtrlSum>9.00</CtrlSum>")
                .getBytes(StandardCharsets.UTF_8);
        byte[] valid = pain001("MSG-MAP-OK", "PMTINF-MAP-OK", "E2E-MAP-OK", "10.00", "EUR")
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(pain001Request(tenantId, invalid, sign(invalid), idempotencyKey))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_FIELD_FORMAT"));

        assertThat(count("SELECT count(*) FROM ingress.idempotency_keys WHERE source_id = '" + tenantId + "' "
                + "AND idem_key = '" + idempotencyKey + "'")).isZero();
        assertThat(count("SELECT count(*) FROM payment.payments")).isZero();

        mockMvc.perform(pain001Request(tenantId, valid, sign(valid), idempotencyKey))
                .andExpect(status().isCreated());

        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-MAP-OK'")).isEqualTo(1);
    }

    @Test
    void replayAfterApprovalKeepsOriginalAcceptedOutcome() throws Exception {
        UUID tenantId = UUID.randomUUID();
        addBroadApprovalRule(tenantId);
        byte[] xml = pain001("MSG-REPLAY-202", "PMTINF-REPLAY-202", "E2E-REPLAY-202", "10.00", "EUR")
                .getBytes(StandardCharsets.UTF_8);
        String idempotencyKey = UUID.randomUUID().toString();

        var firstResponse = mockMvc.perform(pain001Request(tenantId, xml, sign(xml), idempotencyKey))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING_APPROVAL"))
                .andReturn();
        String firstLocation = firstResponse.getResponse().getHeader("Location");
        String paymentId = firstLocation.substring(firstLocation.lastIndexOf('/') + 1);

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/approve")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()).subject("checker-subject"))
                                .authorities(() -> "ROLE_payment_approver"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json"))
                .andExpect(status().isOk());

        var replayResponse = mockMvc.perform(pain001Request(tenantId, xml, sign(xml), idempotencyKey))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING_APPROVAL"))
                .andReturn();

        assertThat(replayResponse.getResponse().getHeader("Location")).isEqualTo(firstLocation);
        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-REPLAY-202'"))
                .isEqualTo(1);
    }

    @Test
    void unauthorizedRoleIsRejected() throws Exception {
        byte[] xml = pain001("MSG-ROLE", "PMTINF-ROLE", "E2E-ROLE", "10.00", "EUR").getBytes(StandardCharsets.UTF_8);
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/iso/pain001")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()))
                                .authorities(() -> "ROLE_payment_viewer"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Signer-Id", participantId.toString())
                        .header("X-Signature", Base64.getEncoder().encodeToString(sign(xml)))
                        .contentType("application/xml")
                        .content(xml))
                .andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM iso.payment_iso_identifiers WHERE end_to_end_id = 'E2E-ROLE'")).isZero();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pain001Request(UUID tenantId,
            byte[] xml, byte[] signature, String idempotencyKey) {
        return pain001Request(tenantId, xml, signature, idempotencyKey, participantId);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pain001Request(UUID tenantId,
            byte[] xml, byte[] signature, String idempotencyKey, UUID signerId) {
        var request = post("/api/v1/iso/pain001")
                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId.toString()))
                        .authorities(() -> "ROLE_payment_submitter"))
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Signer-Id", signerId.toString())
                .contentType("application/xml")
                .content(xml);
        return signature == null ? request : request.header("X-Signature", Base64.getEncoder().encodeToString(signature));
    }

    private byte[] sign(byte[] message) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(signingKeyPair.getPrivate());
        signer.update(message);
        return signer.sign();
    }

    private static String pain001(String msgId, String pmtInfId, String endToEndId, String amount, String currency) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
                  <CstmrCdtTrfInitn>
                    <GrpHdr>
                      <MsgId>%s</MsgId>
                      <CreDtTm>2026-07-15T10:00:00</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
                    </GrpHdr>
                    <PmtInf>
                      <PmtInfId>%s</PmtInfId>
                      <PmtMtd>TRF</PmtMtd>
                      <NbOfTxs>1</NbOfTxs>
                      <CtrlSum>%s</CtrlSum>
                      <DbtrAcct><Id><IBAN>DE89370400440532013000</IBAN></Id></DbtrAcct>
                      <CdtTrfTxInf>
                        <PmtId><EndToEndId>%s</EndToEndId></PmtId>
                        <Amt><InstdAmt Ccy="%s">%s</InstdAmt></Amt>
                        <CdtrAcct><Id><IBAN>FR7630006000011234567890189</IBAN></Id></CdtrAcct>
                      </CdtTrfTxInf>
                    </PmtInf>
                  </CstmrCdtTrfInitn>
                </Document>
                """.formatted(msgId, pmtInfId, amount, endToEndId, currency, amount);
    }

    private static void addBroadApprovalRule(UUID tenantId) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "reference_data_role",
                "dev-only-reference-data");
                PreparedStatement guc = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)");
                PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO reference_data.approval_matrix_rules
                            (tenant_id, requires_approval, requires_step_up, valid_from)
                        VALUES (?, true, false, CURRENT_DATE)
                        """)) {
            guc.setString(1, tenantId.toString());
            guc.execute();
            insert.setObject(1, tenantId);
            insert.executeUpdate();
        }
    }

    private static int count(String sql) throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    static synchronized void initializeDatabase() {
        if (initialized) {
            return;
        }
        POSTGRES.start();
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize PostgreSQL test container", exception);
        }
        org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        initialized = true;
    }

    private static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
