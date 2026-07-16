package com.sepanexus.signature.internal;

import com.sepanexus.signature.KeyPurpose;
import com.sepanexus.signature.KeyStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * EPIC-31 Story 31.3A: reads a {@code signature.signature_keys} row by its exact {@code id} —
 * {@code signingKeyRef} names one specific key, unlike {@link com.sepanexus.signature.KeyRegistryPort}'s
 * {@code (participantId, purpose, asOf)} lookup used by verification. Talks to the {@code
 * signature} schema over the same dedicated {@link SignatureConnectionFactory} every other internal
 * component in this module uses.
 */
@Component
class SigningKeyLookup {

    private final SignatureConnectionFactory connectionFactory;

    SigningKeyLookup(SignatureConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    Optional<SigningKeyRecord> findById(UUID keyId) {
        try (Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT id, purpose, algo, private_material_ref, valid_from, valid_to, status
                        FROM signature.signature_keys
                        WHERE id = ?
                        """)) {
            statement.setObject(1, keyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SigningKeyRecord(
                        (UUID) resultSet.getObject("id"),
                        KeyPurpose.valueOf(resultSet.getString("purpose")),
                        resultSet.getString("algo"),
                        resultSet.getString("private_material_ref"),
                        resultSet.getTimestamp("valid_from").toInstant(),
                        resultSet.getTimestamp("valid_to") == null ? null
                                : resultSet.getTimestamp("valid_to").toInstant(),
                        KeyStatus.valueOf(resultSet.getString("status"))));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not look up signing key", exception);
        }
    }
}
