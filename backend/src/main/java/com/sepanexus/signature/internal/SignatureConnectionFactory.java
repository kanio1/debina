package com.sepanexus.signature.internal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Opens per-call {@code signature_role} connections — shared by every internal component that
 * talks to the {@code signature} schema (key registry, verification), so the dedicated-writer-role
 * boundary (Story 31.1 grant test) is wired in exactly one place.
 */
@Component
class SignatureConnectionFactory {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    SignatureConnectionFactory(
            @Value("${signature.datasource.url}") String jdbcUrl,
            @Value("${signature.datasource.username}") String username,
            @Value("${signature.datasource.password}") String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }
}
