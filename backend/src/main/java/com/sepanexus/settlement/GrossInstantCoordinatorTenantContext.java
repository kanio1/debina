package com.sepanexus.settlement;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Sets the RLS tenant GUC transaction-locally on the coordinator's already-bound connection. */
@Component
public class GrossInstantCoordinatorTenantContext {

    private final JdbcTemplate jdbcTemplate;

    public GrossInstantCoordinatorTenantContext(@Qualifier("grossInstantJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void apply(UUID tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        jdbcTemplate.queryForObject("SELECT pg_catalog.set_config('app.tenant_id', ?::text, true)", String.class,
                tenantId.toString());
    }
}
