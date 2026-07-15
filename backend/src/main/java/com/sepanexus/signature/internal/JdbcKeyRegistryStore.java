package com.sepanexus.signature.internal;

import com.sepanexus.signature.KeyPurpose;
import com.sepanexus.signature.KeyRegistryPort;
import com.sepanexus.signature.KeyStatus;
import com.sepanexus.signature.SignatureKeyRegistration;
import com.sepanexus.signature.SignatureKeyView;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Talks to {@code signature.signature_keys} over {@link SignatureConnectionFactory}'s dedicated
 * {@code signature_role} connection — deliberately not the shared {@code sepa_app} pool every
 * other module currently writes through, so the "no other module role writes signature.*"
 * boundary (Story 31.1 grant test) holds for this module's own runtime writes too.
 */
@Component
public class JdbcKeyRegistryStore implements KeyRegistryPort {

    private final SignatureConnectionFactory connectionFactory;

    public JdbcKeyRegistryStore(SignatureConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public UUID register(SignatureKeyRegistration registration) {
        UUID id = UUID.randomUUID();
        try (Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO signature.signature_keys
                            (id, participant_id, purpose, algo, public_material, private_material_ref,
                             valid_from, valid_to, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                        """)) {
            statement.setObject(1, id);
            statement.setObject(2, registration.participantId());
            statement.setString(3, registration.purpose().name());
            statement.setString(4, registration.algo());
            statement.setString(5, registration.publicMaterial());
            statement.setString(6, registration.privateMaterialRef());
            statement.setTimestamp(7, Timestamp.from(registration.validFrom()));
            statement.setTimestamp(8, registration.validTo() == null ? null : Timestamp.from(registration.validTo()));
            statement.executeUpdate();
            return id;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not register signature key", exception);
        }
    }

    @Override
    public Optional<SignatureKeyView> lookup(UUID participantId, KeyPurpose purpose, Instant asOf) {
        try (Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT id, participant_id, purpose, algo, public_material, valid_from, valid_to, status
                        FROM signature.signature_keys
                        WHERE participant_id IS NOT DISTINCT FROM ?
                          AND (purpose = ? OR purpose = 'BOTH')
                          AND status = 'ACTIVE'
                          AND valid_from <= ?
                          AND (valid_to IS NULL OR ? < valid_to)
                        ORDER BY valid_from DESC
                        LIMIT 1
                        """)) {
            statement.setObject(1, participantId);
            statement.setString(2, purpose.name());
            Timestamp asOfTimestamp = Timestamp.from(asOf);
            statement.setTimestamp(3, asOfTimestamp);
            statement.setTimestamp(4, asOfTimestamp);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SignatureKeyView(
                        (UUID) resultSet.getObject("id"),
                        (UUID) resultSet.getObject("participant_id"),
                        KeyPurpose.valueOf(resultSet.getString("purpose")),
                        resultSet.getString("algo"),
                        resultSet.getString("public_material"),
                        resultSet.getTimestamp("valid_from").toInstant(),
                        resultSet.getTimestamp("valid_to") == null ? null
                                : resultSet.getTimestamp("valid_to").toInstant(),
                        KeyStatus.valueOf(resultSet.getString("status"))));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not look up signature key", exception);
        }
    }

}
