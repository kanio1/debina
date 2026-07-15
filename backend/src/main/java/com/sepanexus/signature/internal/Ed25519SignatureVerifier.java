package com.sepanexus.signature.internal;

import com.sepanexus.signature.DetachedSignature;
import com.sepanexus.signature.KeyPurpose;
import com.sepanexus.signature.KeyRegistryPort;
import com.sepanexus.signature.SignatureKeyView;
import com.sepanexus.signature.SignaturePort;
import com.sepanexus.signature.SignatureVerificationRequest;
import com.sepanexus.signature.Verdict;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Story 31.2: verification on the exact archived raw bytes, never a re-serialized form
 * (sepa-nexus-signature-module-blueprint.md §6/§10). {@code Ed25519} is the module's one synthetic
 * MVP algorithm (§12: "one synthetic algorithm suffices for MVP") — the blueprint does not pin an
 * exact JCA name, so this is recorded as {@code [OPEN-QUESTION]} resolved pragmatically: a real,
 * JDK-native algorithm requiring no external provider, not a placeholder string. Every verify()
 * call — whatever the verdict — writes exactly one {@code signature_verification_events} row
 * (blueprint §5: "append-only verdict log: every verify attempt and its outcome", never
 * deduplicated, matching the platform's raw-evidence-never-deduplicates rule); a {@code VERIFIED}
 * verdict additionally writes one {@code message_signatures} row, in the same transaction.
 */
@Component
public class Ed25519SignatureVerifier implements SignaturePort {

    private static final Set<String> ALLOWED_ALGORITHMS = Set.of("Ed25519");

    private final KeyRegistryPort keyRegistryPort;
    private final SignatureConnectionFactory connectionFactory;

    public Ed25519SignatureVerifier(KeyRegistryPort keyRegistryPort, SignatureConnectionFactory connectionFactory) {
        this.keyRegistryPort = keyRegistryPort;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Verdict verify(SignatureVerificationRequest request) {
        if (!ALLOWED_ALGORITHMS.contains(request.algo())) {
            return recordAndReturn(request, new Verdict(Verdict.Result.FAILED, null, request.algo(),
                    Verdict.REASON_UNSUPPORTED_ALGORITHM));
        }

        boolean signaturePresent = request.signatureBytes() != null && request.signatureBytes().length > 0;
        if (!signaturePresent) {
            Verdict verdict = request.signatureRequired()
                    ? new Verdict(Verdict.Result.FAILED, null, request.algo(), Verdict.REASON_MISSING_REQUIRED_SIGNATURE)
                    : new Verdict(Verdict.Result.NOT_APPLICABLE, null, request.algo(), null);
            return recordAndReturn(request, verdict);
        }

        Optional<SignatureKeyView> key = keyRegistryPort.lookup(request.declaredSignerId(), KeyPurpose.VERIFY,
                request.asOf());
        if (key.isEmpty()) {
            return recordAndReturn(request, new Verdict(Verdict.Result.FAILED, null, request.algo(),
                    Verdict.REASON_KEY_NOT_FOUND_OR_INACTIVE));
        }
        SignatureKeyView activeKey = key.get();
        if (!ALLOWED_ALGORITHMS.contains(activeKey.algo()) || !activeKey.algo().equals(request.algo())) {
            return recordAndReturn(request, new Verdict(Verdict.Result.FAILED, activeKey.id(), request.algo(),
                    Verdict.REASON_UNSUPPORTED_ALGORITHM));
        }

        boolean cryptographicallyValid = verifySignatureBytes(activeKey, request);
        Verdict verdict = cryptographicallyValid
                ? new Verdict(Verdict.Result.VERIFIED, activeKey.id(), activeKey.algo(), null)
                : new Verdict(Verdict.Result.FAILED, activeKey.id(), activeKey.algo(), Verdict.REASON_TAMPERED_OR_INVALID);
        return recordAndReturn(request, verdict);
    }

    @Override
    public DetachedSignature sign(byte[] artifactBytes, String signingKeyRef) {
        throw new UnsupportedOperationException("Signing lands in Story 31.3 (egress) — not implemented yet");
    }

    private boolean verifySignatureBytes(SignatureKeyView key, SignatureVerificationRequest request) {
        try {
            PublicKey publicKey = decodePublicKey(key.publicMaterial());
            Signature verifier = Signature.getInstance(key.algo());
            verifier.initVerify(publicKey);
            verifier.update(request.rawBytes());
            return verifier.verify(request.signatureBytes());
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return false;
        }
    }

    private static PublicKey decodePublicKey(String base64Material) throws GeneralSecurityException {
        byte[] encoded = Base64.getDecoder().decode(base64Material);
        KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
        return keyFactory.generatePublic(new X509EncodedKeySpec(encoded));
    }

    private Verdict recordAndReturn(SignatureVerificationRequest request, Verdict verdict) {
        try (Connection connection = connectionFactory.open()) {
            connection.setAutoCommit(false);
            try {
                insertVerificationEvent(connection, request, verdict);
                if (verdict.result() == Verdict.Result.VERIFIED) {
                    insertMessageSignature(connection, request, verdict);
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not persist signature verification evidence", exception);
        }
        return verdict;
    }

    private void insertVerificationEvent(Connection connection, SignatureVerificationRequest request, Verdict verdict)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO signature.signature_verification_events
                    (id, raw_message_id, verdict, reason_code, key_id, channel, verified_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, request.rawMessageId());
            statement.setString(3, verdict.result().name());
            statement.setString(4, verdict.reasonCode());
            statement.setObject(5, verdict.keyId());
            statement.setString(6, request.channel());
            statement.setTimestamp(7, Timestamp.from(request.asOf()));
            statement.executeUpdate();
        }
    }

    private void insertMessageSignature(Connection connection, SignatureVerificationRequest request, Verdict verdict)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO signature.message_signatures
                    (id, raw_message_id, direction, algo, key_id, signature_bytes, covered_sha256, created_at)
                VALUES (?, ?, 'INBOUND', ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, request.rawMessageId());
            statement.setString(3, verdict.algo());
            statement.setObject(4, verdict.keyId());
            statement.setBytes(5, request.signatureBytes());
            statement.setBytes(6, sha256(request.rawBytes()));
            statement.setTimestamp(7, Timestamp.from(request.asOf()));
            statement.executeUpdate();
        }
    }

    private static byte[] sha256(byte[] payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
