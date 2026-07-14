package com.sepanexus.modules.paymentlifecycle.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Component
public class TenantGucConfigurer {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Sets the tenant GUC on Hibernate's transaction-bound connection.  The third
     * set_config argument scopes it to the current transaction, preventing pool leakage.
     */
    public void apply(UUID tenantId) {
        apply(tenantId, null);
    }

    /**
     * Two-level GUC (EPIC-22 Story 22.1, §4.7): a null/absent {@code branchId} leaves
     * {@code app.branch_id} unset, which the RLS policy treats as "no branch restriction" — the
     * same empty-string-means-unset convention already used for {@code app.tenant_id}.
     */
    public void apply(UUID tenantId, UUID branchId) {
        if (tenantId == null) {
            return;
        }
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT set_config('app.tenant_id', ?, true), set_config('app.branch_id', ?, true)")) {
                statement.setString(1, tenantId.toString());
                statement.setString(2, branchId == null ? "" : branchId.toString());
                statement.execute();
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not set transaction-local tenant context", exception);
            }
        });
    }
}
