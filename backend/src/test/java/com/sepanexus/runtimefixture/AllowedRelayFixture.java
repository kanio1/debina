package com.sepanexus.runtimefixture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test-only positive fixture for a relay-owned dependency. */
class AllowedRelayFixture {
    AllowedRelayFixture(@Qualifier("outboxRelayJdbcTemplate") JdbcTemplate jdbcTemplate) {
    }
}
