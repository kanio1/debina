package com.sepanexus.settlement;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Opens settlement's own authority connection; it has no payment or ledger table grants. */
@Component
public class SettlementConnectionFactory {
    private final String url;
    private final String username;
    private final String password;

    public SettlementConnectionFactory(@Value("${settlement.datasource.url}") String url,
            @Value("${settlement.datasource.username}") String username,
            @Value("${settlement.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    Connection open() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
