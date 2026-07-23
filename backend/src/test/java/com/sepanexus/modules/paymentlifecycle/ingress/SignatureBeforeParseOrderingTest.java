package com.sepanexus.modules.paymentlifecycle.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sepanexus.SepaNexusApplication;
import com.sepanexus.signature.KeyPurpose;
import com.sepanexus.signature.KeyRegistryPort;
import com.sepanexus.signature.SignatureKeyRegistration;
import com.sepanexus.signature.SignatureVerificationPort;
import com.sepanexus.signature.Verdict;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * EPIC-19 Story 19.2 / EPIC-31 Story 31.2 (shared test — G1, verify-before-parse): proves the
 * enforced order {@code archive → verify → parse} on {@link SignedChannelIngestionPipeline}, using
 * real collaborators wrapped in call-recording spies (never mocking the cryptographic mechanism
 * itself — {@link SignatureBeforeParseOrderingTest#signaturePort} still runs real Ed25519
 * verification underneath the spy).
 */
@SpringBootTest(classes = SepaNexusApplication.class)
@org.junit.jupiter.api.Tag("testcontainers")
class SignatureBeforeParseOrderingTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("sepa_nexus")
            .withUsername("test_admin")
            .withPassword("test_admin");
    private static boolean initialized;
    private static KeyPair signingKeyPair;

    @Autowired
    private SignedChannelIngestionPipeline pipeline;

    @Autowired
    private KeyRegistryPort keyRegistryPort;

    @MockitoSpyBean
    private RawMessageArchive rawMessageArchive;

    @MockitoSpyBean
    private SignatureVerificationPort signaturePort;

    @MockitoSpyBean
    private HardenedXmlFactory hardenedXmlFactory;

    private final UUID participantId = UUID.randomUUID();
    private final byte[] xmlBytes = "<?xml version=\"1.0\"?><Document><GrpHdr><MsgId>MSG-1</MsgId></GrpHdr></Document>"
            .getBytes(StandardCharsets.UTF_8);

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
            statement.execute("TRUNCATE ingress.raw_inbound_messages CASCADE");
            statement.execute("TRUNCATE signature.signature_keys CASCADE");
        }
        keyRegistryPort.register(new SignatureKeyRegistration(participantId, KeyPurpose.VERIFY, "Ed25519",
                Base64.getEncoder().encodeToString(signingKeyPair.getPublic().getEncoded()), "synthetic-lab-ref",
                Instant.now().minus(1, ChronoUnit.HOURS), null));
    }

    @Test
    void validSignatureArchivesVerifiesThenParsesExactlyOnce() throws Exception {
        byte[] signature = sign(xmlBytes);

        SignedChannelIngestionPipeline.PipelineResult result = pipeline.ingest("bank-xml", UUID.randomUUID(),
                "pain.001", xmlBytes, signature, participantId, "Ed25519", true, Instant.now());

        assertThat(result.verdict().result()).isEqualTo(Verdict.Result.VERIFIED);
        assertThat(result.parseResult()).isNotNull();
        assertThat(result.parseResult().accepted()).isTrue();

        InOrder order = inOrder(rawMessageArchive, signaturePort, hardenedXmlFactory);
        order.verify(rawMessageArchive).archive(any(), any(), any(), any());
        order.verify(signaturePort).verify(any());
        order.verify(hardenedXmlFactory).parse(any());
    }

    @Test
    void tamperedSignatureStopsBeforeParsingButStillArchivesAndVerifies() throws Exception {
        byte[] signature = sign(xmlBytes);
        signature[0] = (byte) (signature[0] + 1);

        SignedChannelIngestionPipeline.PipelineResult result = pipeline.ingest("bank-xml", UUID.randomUUID(),
                "pain.001", xmlBytes, signature, participantId, "Ed25519", true, Instant.now());

        assertThat(result.verdict().result()).isEqualTo(Verdict.Result.FAILED);
        assertThat(result.parseResult()).isNull();

        verify(rawMessageArchive).archive(any(), any(), any(), any());
        verify(signaturePort).verify(any());
        verify(hardenedXmlFactory, never()).parse(any());
    }

    @Test
    void missingRequiredSignatureStopsBeforeParsingButStillArchives() throws Exception {
        SignedChannelIngestionPipeline.PipelineResult result = pipeline.ingest("bank-xml", UUID.randomUUID(),
                "pain.001", xmlBytes, null, participantId, "Ed25519", true, Instant.now());

        assertThat(result.verdict().result()).isEqualTo(Verdict.Result.FAILED);
        assertThat(result.parseResult()).isNull();

        verify(rawMessageArchive).archive(any(), any(), any(), any());
        verify(hardenedXmlFactory, never()).parse(any());
    }

    @Test
    void jsonDirectStyleOptionalChannelWithNoSignatureStillReachesParser() throws Exception {
        SignedChannelIngestionPipeline.PipelineResult result = pipeline.ingest("json-direct", UUID.randomUUID(),
                "JSON_DIRECT", xmlBytes, null, participantId, "Ed25519", false, Instant.now());

        assertThat(result.verdict().result()).isEqualTo(Verdict.Result.NOT_APPLICABLE);
        assertThat(result.parseResult()).isNotNull();
        verify(hardenedXmlFactory).parse(any());
    }

    private static byte[] sign(byte[] message) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(signingKeyPair.getPrivate());
        signer.update(message);
        return signer.sign();
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
