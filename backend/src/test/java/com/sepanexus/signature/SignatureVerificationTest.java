package com.sepanexus.signature;

import static org.assertj.core.api.Assertions.assertThat;

import com.sepanexus.SepaNexusApplication;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * EPIC-31 Story 31.2: real Ed25519 cryptographic verification against a real database — covers
 * every scenario the story requires. Nothing here mocks the cryptographic mechanism itself; the
 * only mocking in this module's test suite (spy on collaborators for call-ordering) lives in
 * {@link com.sepanexus.modules.paymentlifecycle.ingress.SignatureBeforeParseOrderingTest}.
 */
@SpringBootTest(classes = SepaNexusApplication.class)
class SignatureVerificationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");
    private static boolean initialized;
    private static KeyPair signingKeyPair;
    private static KeyPair otherKeyPair;

    @Autowired
    private SignatureVerificationPort signaturePort;

    @Autowired
    private KeyRegistryPort keyRegistryPort;

    private final UUID participantId = UUID.randomUUID();
    private final byte[] rawBytes = "<Document><GrpHdr><MsgId>MSG-1</MsgId></GrpHdr></Document>"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

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
    static void generateKeyPairs() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        signingKeyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE ingress.raw_inbound_messages CASCADE");
            statement.execute("TRUNCATE signature.signature_keys CASCADE");
        }
    }

    @Test
    void validSignatureIsVerifiedAndEvidenceIsPersisted() throws Exception {
        registerKey(participantId, signingKeyPair, Instant.now().minus(1, ChronoUnit.HOURS), null);
        UUID rawMessageId = archiveRawMessage();
        byte[] signature = sign(signingKeyPair, rawBytes);

        Verdict verdict = signaturePort.verify(new SignatureVerificationRequest(rawMessageId, rawBytes, signature,
                participantId, "Ed25519", "bank-xml", true, Instant.now()));

        assertThat(verdict.result()).isEqualTo(Verdict.Result.VERIFIED);
        assertThat(verdict.reasonCode()).isNull();
        assertThat(countRows("signature.signature_verification_events", rawMessageId)).isEqualTo(1);
        assertThat(countRows("signature.message_signatures", rawMessageId)).isEqualTo(1);
    }

    @Test
    void tamperedPayloadFailsAndIsNotRecordedAsAMessageSignature() throws Exception {
        registerKey(participantId, signingKeyPair, Instant.now().minus(1, ChronoUnit.HOURS), null);
        UUID rawMessageId = archiveRawMessage();
        byte[] signature = sign(signingKeyPair, rawBytes);
        byte[] tamperedBytes = rawBytes.clone();
        tamperedBytes[0] = (byte) (tamperedBytes[0] + 1);

        Verdict verdict = signaturePort.verify(new SignatureVerificationRequest(rawMessageId, tamperedBytes,
                signature, participantId, "Ed25519", "bank-xml", true, Instant.now()));

        assertThat(verdict.result()).isEqualTo(Verdict.Result.FAILED);
        assertThat(verdict.reasonCode()).isEqualTo(Verdict.REASON_TAMPERED_OR_INVALID);
        assertThat(countRows("signature.message_signatures", rawMessageId)).isZero();
        assertThat(countRows("signature.signature_verification_events", rawMessageId)).isEqualTo(1);
    }

    @Test
    void tamperedSignatureFails() throws Exception {
        registerKey(participantId, signingKeyPair, Instant.now().minus(1, ChronoUnit.HOURS), null);
        UUID rawMessageId = archiveRawMessage();
        byte[] signature = sign(signingKeyPair, rawBytes);
        signature[0] = (byte) (signature[0] + 1);

        Verdict verdict = signaturePort.verify(new SignatureVerificationRequest(rawMessageId, rawBytes, signature,
                participantId, "Ed25519", "bank-xml", true, Instant.now()));

        assertThat(verdict.result()).isEqualTo(Verdict.Result.FAILED);
        assertThat(verdict.reasonCode()).isEqualTo(Verdict.REASON_TAMPERED_OR_INVALID);
    }

    @Test
    void wrongButActiveKeyFails() throws Exception {
        registerKey(participantId, signingKeyPair, Instant.now().minus(1, ChronoUnit.HOURS), null);
        UUID rawMessageId = archiveRawMessage();
        byte[] signature = sign(otherKeyPair, rawBytes);

        Verdict verdict = signaturePort.verify(new SignatureVerificationRequest(rawMessageId, rawBytes, signature,
                participantId, "Ed25519", "bank-xml", true, Instant.now()));

        assertThat(verdict.result()).isEqualTo(Verdict.Result.FAILED);
        assertThat(verdict.reasonCode()).isEqualTo(Verdict.REASON_TAMPERED_OR_INVALID);
    }

    @Test
    void unknownKeyFails() throws Exception {
        UUID rawMessageId = archiveRawMessage();
        byte[] signature = sign(signingKeyPair, rawBytes);

        Verdict verdict = signaturePort.verify(new SignatureVerificationRequest(rawMessageId, rawBytes, signature,
                UUID.randomUUID(), "Ed25519", "bank-xml", true, Instant.now()));

        assertThat(verdict.result()).isEqualTo(Verdict.Result.FAILED);
        assertThat(verdict.reasonCode()).isEqualTo(Verdict.REASON_KEY_NOT_FOUND_OR_INACTIVE);
    }

    @Test
    void expiredKeyFails() throws Exception {
        registerKey(participantId, signingKeyPair, Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS));
        UUID rawMessageId = archiveRawMessage();
        byte[] signature = sign(signingKeyPair, rawBytes);

        Verdict verdict = signaturePort.verify(new SignatureVerificationRequest(rawMessageId, rawBytes, signature,
                participantId, "Ed25519", "bank-xml", true, Instant.now()));

        assertThat(verdict.result()).isEqualTo(Verdict.Result.FAILED);
        assertThat(verdict.reasonCode()).isEqualTo(Verdict.REASON_KEY_NOT_FOUND_OR_INACTIVE);
    }

    @Test
    void futureKeyFails() throws Exception {
        registerKey(participantId, signingKeyPair, Instant.now().plus(1, ChronoUnit.HOURS), null);
        UUID rawMessageId = archiveRawMessage();
        byte[] signature = sign(signingKeyPair, rawBytes);

        Verdict verdict = signaturePort.verify(new SignatureVerificationRequest(rawMessageId, rawBytes, signature,
                participantId, "Ed25519", "bank-xml", true, Instant.now()));

        assertThat(verdict.result()).isEqualTo(Verdict.Result.FAILED);
        assertThat(verdict.reasonCode()).isEqualTo(Verdict.REASON_KEY_NOT_FOUND_OR_INACTIVE);
    }

    @Test
    void unsupportedAlgorithmFailsBeforeKeyLookup() throws Exception {
        UUID rawMessageId = archiveRawMessage();

        Verdict verdict = signaturePort.verify(new SignatureVerificationRequest(rawMessageId, rawBytes,
                "not-a-real-signature".getBytes(java.nio.charset.StandardCharsets.UTF_8), participantId,
                "RSA-SHA256", "bank-xml", true, Instant.now()));

        assertThat(verdict.result()).isEqualTo(Verdict.Result.FAILED);
        assertThat(verdict.reasonCode()).isEqualTo(Verdict.REASON_UNSUPPORTED_ALGORITHM);
    }

    @Test
    void missingSignatureOnARequiredChannelFails() throws Exception {
        UUID rawMessageId = archiveRawMessage();

        Verdict verdict = signaturePort.verify(new SignatureVerificationRequest(rawMessageId, rawBytes, null,
                participantId, "Ed25519", "bank-xml", true, Instant.now()));

        assertThat(verdict.result()).isEqualTo(Verdict.Result.FAILED);
        assertThat(verdict.reasonCode()).isEqualTo(Verdict.REASON_MISSING_REQUIRED_SIGNATURE);
    }

    @Test
    void missingSignatureOnAnOptionalChannelIsNotApplicable() throws Exception {
        UUID rawMessageId = archiveRawMessage();

        Verdict verdict = signaturePort.verify(new SignatureVerificationRequest(rawMessageId, rawBytes, null,
                participantId, "Ed25519", "json-direct", false, Instant.now()));

        assertThat(verdict.result()).isEqualTo(Verdict.Result.NOT_APPLICABLE);
        assertThat(verdict.reasonCode()).isNull();
    }

    private void registerKey(UUID forParticipantId, KeyPair keyPair, Instant validFrom, Instant validTo) {
        keyRegistryPort.register(new SignatureKeyRegistration(forParticipantId, KeyPurpose.VERIFY, "Ed25519",
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()), "synthetic-lab-ref", validFrom,
                validTo));
    }

    private static byte[] sign(KeyPair keyPair, byte[] message) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(message);
        return signer.sign();
    }

    private UUID archiveRawMessage() throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ingress.raw_inbound_messages (id, channel, tenant_id, message_type, payload, payload_sha256)
                VALUES (?, 'bank-xml', ?, 'pain.001', ?, ?)
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, UUID.randomUUID());
            statement.setBytes(3, rawBytes);
            statement.setBytes(4, java.security.MessageDigest.getInstance("SHA-256").digest(rawBytes));
            statement.executeUpdate();
        }
        return id;
    }

    private int countRows(String table, UUID rawMessageId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM " + table + " WHERE raw_message_id = ?")) {
            statement.setObject(1, rawMessageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
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
