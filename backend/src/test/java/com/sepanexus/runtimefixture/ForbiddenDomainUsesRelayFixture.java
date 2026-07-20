package com.sepanexus.modules.paymentlifecycle.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test-only deliberate violation detected by RuntimeDatasourceOwnershipTest's fixture oracle. */
class ForbiddenDomainUsesRelayFixture {
    ForbiddenDomainUsesRelayFixture(@Qualifier("outboxRelayJdbcTemplate") JdbcTemplate jdbcTemplate) {
    }
}
