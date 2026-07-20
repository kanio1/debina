package com.sepanexus.ledger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Opens the ledger module's dedicated writer identity, never the shared application role. */
@Component
class LedgerConnectionFactory {
    private final String url;
    private final String username;
    private final String password;

    LedgerConnectionFactory(@Value("${ledger.datasource.url}") String url,
            @Value("${ledger.datasource.username}") String username,
            @Value("${ledger.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    Connection open() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
