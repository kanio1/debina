package com.sepanexus.signature.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sepanexus.shared.ClockPort;
import com.sepanexus.signature.DetachedSignature;
import com.sepanexus.signature.KeyPurpose;
import com.sepanexus.signature.SignatureVerificationPort;
import com.sepanexus.signature.SignatureVerificationRequest;
import com.sepanexus.signature.SigningException;
import com.sepanexus.signature.Verdict;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * EPIC-31 Story 31.3A: standalone signing capability, round-trip sign→verify through a stub
 * caller — no {@code egress} dependency (see {@code planning/epics/EPIC-31-signature-module.md}).
 * Real, isolated Testcontainers PostgreSQL (see {@code sepa-nexus-database-testing} skill), real
 * Ed25519 JCA (nothing mocked), fixed {@link ClockPort}. No Kafka container — nothing here needs a
 * broker. Classes constructed directly (no Spring context), matching the established convention in
 * {@code OrphanDlqTest}/{@code Pacs002CorrelationPersistenceTest}.
 */
@Testcontainers
@org.junit.jupiter.api.Tag("testcontainers")
class SigningRoundTripTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");

    private static final Instant FIXED_NOW = Instant.parse("2026-07-16T12:00:00Z");
    private static final ClockPort FIXED_CLOCK = () -> FIXED_NOW;

    private SignatureConnectionFactory connectionFactory;
    private SignatureVerificationPort verificationPort;
    private Ed25519SignatureSigner signer;

    @BeforeAll
    static void migrateDatabase() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void freshState() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE signature.signature_keys CASCADE");
            statement.execute("TRUNCATE signature.message_signatures CASCADE");
        }
        connectionFactory = new SignatureConnectionFactory(POSTGRES.getJdbcUrl(), "signature_role",
                "dev-only-signature");
        verificationPort = new Ed25519SignatureVerifier(new JdbcKeyRegistryStore(connectionFactory),
                connectionFactory);
        signer = new Ed25519SignatureSigner(new SigningKeyLookup(connectionFactory),
                new PrivateKeyMaterialResolver(), connectionFactory, FIXED_CLOCK);
    }

    @Test
    void signThenVerifyRoundTripsToVerified() throws Exception {
        KeyPair keyPair = generateEd25519KeyPair();
        UUID participantId = UUID.randomUUID();
        UUID keyId = registerKey(participantId, KeyPurpose.BOTH, keyPair, FIXED_NOW.minus(1, ChronoUnit.HOURS), null);
        byte[] artifactBytes = "<Document><pacs.002/></Document>".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        DetachedSignature signature = signer.sign(artifactBytes, keyId.toString());

        assertThat(signature.signatureBytes()).isNotEmpty();
        assertThat(signature.keyId()).isEqualTo(keyId);
        assertThat(signature.algo()).isEqualTo("Ed25519");

        Verdict verdict = verificationPort.verify(new SignatureVerificationRequest(null, artifactBytes,
                signature.signatureBytes(), participantId, "Ed25519", "standalone-signing", true, FIXED_NOW));
        assertThat(verdict.result()).isEqualTo(Verdict.Result.VERIFIED);
    }

    @Test
    void tamperedArtifactAfterSigningFailsVerification() throws Exception {
        KeyPair keyPair = generateEd25519KeyPair();
        UUID participantId = UUID.randomUUID();
        UUID keyId = registerKey(participantId, KeyPurpose.BOTH, keyPair, FIXED_NOW.minus(1, ChronoUnit.HOURS), null);
        byte[] artifactBytes = "<Document><pacs.002/></Document>".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        DetachedSignature signature = signer.sign(artifactBytes, keyId.toString());

        byte[] tampered = artifactBytes.clone();
        tampered[0] = (byte) (tampered[0] + 1);

        Verdict verdict = verificationPort.verify(new SignatureVerificationRequest(null, tampered,
                signature.signatureBytes(), participantId, "Ed25519", "standalone-signing", true, FIXED_NOW));
        assertThat(verdict.result()).isEqualTo(Verdict.Result.FAILED);
        assertThat(verdict.reasonCode()).isEqualTo(Verdict.REASON_TAMPERED_OR_INVALID);
    }

    @Test
    void signPersistsExactlyOneOutboundMessageSignatureRow() throws Exception {
        KeyPair keyPair = generateEd25519KeyPair();
        UUID keyId = registerKey(UUID.randomUUID(), KeyPurpose.SIGN, keyPair, FIXED_NOW.minus(1, ChronoUnit.HOURS),
                null);
        byte[] artifactBytes = "artifact-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        DetachedSignature signature = signer.sign(artifactBytes, keyId.toString());

        try (Connection connection = adminConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT direction, raw_message_id, outbound_artifact_id, algo, key_id,
                               signature_bytes, covered_sha256, created_at
                        FROM signature.message_signatures
                        WHERE key_id = ?
                        """)) {
            statement.setObject(1, keyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("direction")).isEqualTo("OUTBOUND");
                assertThat(resultSet.getObject("raw_message_id")).isNull();
                assertThat(resultSet.getObject("outbound_artifact_id")).isNull();
                assertThat(resultSet.getString("algo")).isEqualTo("Ed25519");
                assertThat((UUID) resultSet.getObject("key_id")).isEqualTo(keyId);
                assertThat(resultSet.getBytes("signature_bytes")).isEqualTo(signature.signatureBytes());
                assertThat(resultSet.getBytes("covered_sha256"))
                        .isEqualTo(java.security.MessageDigest.getInstance("SHA-256").digest(artifactBytes));
                assertThat(resultSet.getTimestamp("created_at").toInstant()).isEqualTo(FIXED_NOW);
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    @Test
    void unknownKeyRefIsRejectedAndPersistsNoRow() {
        SigningException exception = assertThrows(SigningException.class,
                () -> signer.sign("bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        UUID.randomUUID().toString()));

        assertThat(exception.reasonCode()).isEqualTo(SigningException.REASON_SIGNING_KEY_NOT_FOUND);
        assertThat(countOutboundRows()).isZero();
    }

    @Test
    void invalidKeyRefFormatIsRejectedAndPersistsNoRow() {
        SigningException exception = assertThrows(SigningException.class,
                () -> signer.sign("bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8), "not-a-uuid"));

        assertThat(exception.reasonCode()).isEqualTo(SigningException.REASON_INVALID_SIGNING_KEY_REF);
        assertThat(countOutboundRows()).isZero();
    }

    @Test
    void verifyOnlyKeyIsRejectedForSigningAndPersistsNoRow() throws Exception {
        KeyPair keyPair = generateEd25519KeyPair();
        UUID keyId = registerKey(UUID.randomUUID(), KeyPurpose.VERIFY, keyPair, FIXED_NOW.minus(1, ChronoUnit.HOURS),
                null);

        SigningException exception = assertThrows(SigningException.class,
                () -> signer.sign("bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8), keyId.toString()));

        assertThat(exception.reasonCode()).isEqualTo(SigningException.REASON_SIGNING_KEY_WRONG_PURPOSE);
        assertThat(countOutboundRows()).isZero();
    }

    @Test
    void futureKeyIsRejectedAndPersistsNoRow() throws Exception {
        KeyPair keyPair = generateEd25519KeyPair();
        UUID keyId = registerKey(UUID.randomUUID(), KeyPurpose.SIGN, keyPair, FIXED_NOW.plus(1, ChronoUnit.HOURS),
                null);

        SigningException exception = assertThrows(SigningException.class,
                () -> signer.sign("bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8), keyId.toString()));

        assertThat(exception.reasonCode()).isEqualTo(SigningException.REASON_SIGNING_KEY_NOT_ACTIVE);
        assertThat(countOutboundRows()).isZero();
    }

    @Test
    void expiredKeyIsRejectedAndPersistsNoRow() throws Exception {
        KeyPair keyPair = generateEd25519KeyPair();
        UUID keyId = registerKey(UUID.randomUUID(), KeyPurpose.SIGN, keyPair, FIXED_NOW.minus(2, ChronoUnit.HOURS),
                FIXED_NOW.minus(1, ChronoUnit.HOURS));

        SigningException exception = assertThrows(SigningException.class,
                () -> signer.sign("bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8), keyId.toString()));

        assertThat(exception.reasonCode()).isEqualTo(SigningException.REASON_SIGNING_KEY_NOT_ACTIVE);
        assertThat(countOutboundRows()).isZero();
    }

    @Test
    void revokedKeyIsRejectedAndPersistsNoRow() throws Exception {
        KeyPair keyPair = generateEd25519KeyPair();
        UUID keyId = registerKey(UUID.randomUUID(), KeyPurpose.SIGN, keyPair, FIXED_NOW.minus(1, ChronoUnit.HOURS),
                null);
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("UPDATE signature.signature_keys SET status = 'REVOKED' WHERE id = '" + keyId + "'");
        }

        SigningException exception = assertThrows(SigningException.class,
                () -> signer.sign("bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8), keyId.toString()));

        assertThat(exception.reasonCode()).isEqualTo(SigningException.REASON_SIGNING_KEY_NOT_ACTIVE);
        assertThat(countOutboundRows()).isZero();
    }

    @Test
    void unsupportedAlgorithmIsRejectedAndPersistsNoRow() throws Exception {
        UUID keyId = UUID.randomUUID();
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO signature.signature_keys
                    (id, participant_id, purpose, algo, public_material, private_material_ref, valid_from, valid_to)
                VALUES (?, ?, 'SIGN', 'RSA-2048', 'irrelevant', 'inline-pkcs8:AAAA', ?, NULL)
                """)) {
            statement.setObject(1, keyId);
            statement.setObject(2, UUID.randomUUID());
            statement.setTimestamp(3, Timestamp.from(FIXED_NOW.minus(1, ChronoUnit.HOURS)));
            statement.executeUpdate();
        }

        SigningException exception = assertThrows(SigningException.class,
                () -> signer.sign("bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8), keyId.toString()));

        assertThat(exception.reasonCode()).isEqualTo(SigningException.REASON_UNSUPPORTED_SIGNING_ALGORITHM);
        assertThat(countOutboundRows()).isZero();
    }

    @Test
    void invalidPrivateKeyMaterialIsRejectedAndPersistsNoRowAndNeverAppearsInException() throws Exception {
        UUID keyId = UUID.randomUUID();
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO signature.signature_keys
                    (id, participant_id, purpose, algo, public_material, private_material_ref, valid_from, valid_to)
                VALUES (?, ?, 'SIGN', 'Ed25519', 'irrelevant', 'not-a-valid-reference-format', ?, NULL)
                """)) {
            statement.setObject(1, keyId);
            statement.setObject(2, UUID.randomUUID());
            statement.setTimestamp(3, Timestamp.from(FIXED_NOW.minus(1, ChronoUnit.HOURS)));
            statement.executeUpdate();
        }

        SigningException exception = assertThrows(SigningException.class,
                () -> signer.sign("bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8), keyId.toString()));

        assertThat(exception.reasonCode()).isEqualTo(SigningException.REASON_INVALID_PRIVATE_KEY_MATERIAL);
        assertThat(exception.getMessage()).doesNotContain("not-a-valid-reference-format");
        assertThat(countOutboundRows()).isZero();
    }

    @Test
    void nullArtifactBytesIsRejected() throws Exception {
        KeyPair keyPair = generateEd25519KeyPair();
        UUID keyId = registerKey(UUID.randomUUID(), KeyPurpose.SIGN, keyPair, FIXED_NOW.minus(1, ChronoUnit.HOURS),
                null);

        SigningException exception = assertThrows(SigningException.class,
                () -> signer.sign(null, keyId.toString()));

        assertThat(exception.reasonCode()).isEqualTo(SigningException.REASON_INVALID_ARTIFACT_BYTES);
        assertThat(countOutboundRows()).isZero();
    }

    @Test
    void emptyArtifactBytesIsAllowedNotBannedByThisImplementation() throws Exception {
        // Decision (Part 6/12 of this session's own prompt: "don't invent a ban on signing empty
        // bytes if the source or JCA allows it"): neither the blueprint nor java.security.Signature
        // forbids signing a zero-length byte sequence, so this implementation does not add one.
        KeyPair keyPair = generateEd25519KeyPair();
        UUID keyId = registerKey(UUID.randomUUID(), KeyPurpose.SIGN, keyPair, FIXED_NOW.minus(1, ChronoUnit.HOURS),
                null);

        DetachedSignature signature = signer.sign(new byte[0], keyId.toString());

        assertThat(signature.signatureBytes()).isNotEmpty();
    }

    @Test
    void nullSigningKeyRefIsRejected() {
        SigningException exception = assertThrows(SigningException.class,
                () -> signer.sign("bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8), null));

        assertThat(exception.reasonCode()).isEqualTo(SigningException.REASON_INVALID_SIGNING_KEY_REF);
    }

    @Test
    void blankSigningKeyRefIsRejected() {
        SigningException exception = assertThrows(SigningException.class,
                () -> signer.sign("bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8), "   "));

        assertThat(exception.reasonCode()).isEqualTo(SigningException.REASON_INVALID_SIGNING_KEY_REF);
    }

    private int countOutboundRows() {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement();
                ResultSet resultSet = statement
                        .executeQuery("SELECT count(*) FROM signature.message_signatures WHERE direction = 'OUTBOUND'")) {
            resultSet.next();
            return resultSet.getInt(1);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private UUID registerKey(UUID participantId, KeyPurpose purpose, KeyPair keyPair, Instant validFrom,
            Instant validTo) throws Exception {
        String publicMaterial = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String privateMaterialRef = "inline-pkcs8:"
                + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        UUID id = UUID.randomUUID();
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO signature.signature_keys
                    (id, participant_id, purpose, algo, public_material, private_material_ref, valid_from, valid_to)
                VALUES (?, ?, ?, 'Ed25519', ?, ?, ?, ?)
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, participantId);
            statement.setString(3, purpose.name());
            statement.setString(4, publicMaterial);
            statement.setString(5, privateMaterialRef);
            statement.setTimestamp(6, Timestamp.from(validFrom));
            statement.setTimestamp(7, validTo == null ? null : Timestamp.from(validTo));
            statement.executeUpdate();
        }
        return id;
    }

    private static KeyPair generateEd25519KeyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "test_admin", "test_admin");
    }
}
