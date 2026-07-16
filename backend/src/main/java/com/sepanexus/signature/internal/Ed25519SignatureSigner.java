package com.sepanexus.signature.internal;

import com.sepanexus.shared.ClockPort;
import com.sepanexus.signature.DetachedSignature;
import com.sepanexus.signature.KeyPurpose;
import com.sepanexus.signature.KeyStatus;
import com.sepanexus.signature.SignatureSigningPort;
import com.sepanexus.signature.SigningException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * EPIC-31 Story 31.3A: standalone signing capability (sign→verify round trip), no dependency on
 * {@code egress} — the actual invocation from egress's renderer is {@code EPIC-43} Story 43.2.
 * {@code signingKeyRef} is the textual {@link UUID} of the {@code signature.signature_keys} row to
 * sign with (a local, documented convention — the source blueprint names the argument but not its
 * exact semantics); the caller names which key, this class resolves its own record rather than
 * trusting any key material the caller might supply. {@code Ed25519} is the module's one allowlisted
 * algorithm (same convention as {@link Ed25519SignatureVerifier}). Every successful {@code sign()}
 * writes exactly one {@code signature.message_signatures} row ({@code direction=OUTBOUND}); {@code
 * raw_message_id}/{@code outbound_artifact_id} both stay {@code NULL} here — this story has no
 * inbound message and no egress artifact yet (that link is {@code EPIC-43} Story 43.2's concern). A
 * failed persistence insert throws rather than returning a signature the caller would believe was
 * durably recorded.
 */
@Component
public class Ed25519SignatureSigner implements SignatureSigningPort {

    private static final Set<String> ALLOWED_ALGORITHMS = Set.of("Ed25519");

    private final SigningKeyLookup signingKeyLookup;
    private final PrivateKeyMaterialResolver privateKeyMaterialResolver;
    private final SignatureConnectionFactory connectionFactory;
    private final ClockPort clockPort;

    public Ed25519SignatureSigner(SigningKeyLookup signingKeyLookup,
            PrivateKeyMaterialResolver privateKeyMaterialResolver, SignatureConnectionFactory connectionFactory,
            ClockPort clockPort) {
        this.signingKeyLookup = signingKeyLookup;
        this.privateKeyMaterialResolver = privateKeyMaterialResolver;
        this.connectionFactory = connectionFactory;
        this.clockPort = clockPort;
    }

    @Override
    public DetachedSignature sign(byte[] artifactBytes, String signingKeyRef) {
        if (artifactBytes == null) {
            throw new SigningException(SigningException.REASON_INVALID_ARTIFACT_BYTES);
        }
        UUID keyId = parseKeyId(signingKeyRef);
        SigningKeyRecord key = signingKeyLookup.findById(keyId)
                .orElseThrow(() -> new SigningException(SigningException.REASON_SIGNING_KEY_NOT_FOUND));

        Instant asOf = clockPort.now();
        if (key.status() != KeyStatus.ACTIVE || key.validFrom().isAfter(asOf)
                || (key.validTo() != null && !asOf.isBefore(key.validTo()))) {
            throw new SigningException(SigningException.REASON_SIGNING_KEY_NOT_ACTIVE);
        }
        if (key.purpose() != KeyPurpose.SIGN && key.purpose() != KeyPurpose.BOTH) {
            throw new SigningException(SigningException.REASON_SIGNING_KEY_WRONG_PURPOSE);
        }
        if (!ALLOWED_ALGORITHMS.contains(key.algo())) {
            throw new SigningException(SigningException.REASON_UNSUPPORTED_SIGNING_ALGORITHM);
        }
        if (key.privateMaterialRef() == null) {
            throw new SigningException(SigningException.REASON_PRIVATE_KEY_MATERIAL_UNAVAILABLE);
        }

        PrivateKey privateKey = privateKeyMaterialResolver.resolve(key.privateMaterialRef());
        byte[] signatureBytes = signBytes(privateKey, key.algo(), artifactBytes);
        byte[] coveredSha256 = sha256(artifactBytes);

        persist(keyId, key.algo(), signatureBytes, coveredSha256, asOf);

        return new DetachedSignature(signatureBytes, keyId, key.algo());
    }

    private static UUID parseKeyId(String signingKeyRef) {
        if (signingKeyRef == null || signingKeyRef.isBlank()) {
            throw new SigningException(SigningException.REASON_INVALID_SIGNING_KEY_REF);
        }
        try {
            return UUID.fromString(signingKeyRef);
        } catch (IllegalArgumentException exception) {
            throw new SigningException(SigningException.REASON_INVALID_SIGNING_KEY_REF);
        }
    }

    private static byte[] signBytes(PrivateKey privateKey, String algo, byte[] artifactBytes) {
        try {
            Signature signer = Signature.getInstance(algo);
            signer.initSign(privateKey);
            signer.update(artifactBytes);
            return signer.sign();
        } catch (GeneralSecurityException exception) {
            throw new SigningException(SigningException.REASON_INVALID_PRIVATE_KEY_MATERIAL, exception);
        }
    }

    private static byte[] sha256(byte[] payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void persist(UUID keyId, String algo, byte[] signatureBytes, byte[] coveredSha256, Instant createdAt) {
        try (Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO signature.message_signatures
                            (id, raw_message_id, outbound_artifact_id, direction, algo, key_id,
                             signature_bytes, covered_sha256, created_at)
                        VALUES (?, NULL, NULL, 'OUTBOUND', ?, ?, ?, ?, ?)
                        """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, algo);
            statement.setObject(3, keyId);
            statement.setBytes(4, signatureBytes);
            statement.setBytes(5, coveredSha256);
            statement.setTimestamp(6, Timestamp.from(createdAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new SigningException(SigningException.REASON_SIGNATURE_PERSISTENCE_FAILED, exception);
        }
    }
}
