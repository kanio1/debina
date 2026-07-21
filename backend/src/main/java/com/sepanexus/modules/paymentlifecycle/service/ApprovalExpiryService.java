package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.shared.ClockPort;
import java.sql.Timestamp;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded, replay-safe worker that has no direct payment-table DML privileges. */
@Service
public class ApprovalExpiryService {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;
    private final ClockPort clock;

    public ApprovalExpiryService(@Qualifier("approvalExpiryJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("approvalExpiryTransactionManager") PlatformTransactionManager transactionManager, ClockPort clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public int expireDueApprovals(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("Expiry limit must be between 1 and 500");
        return transactions.execute(status -> jdbcTemplate.queryForObject(
                "SELECT payment.expire_due_approvals(?, ?)", Integer.class, Timestamp.from(clock.now()), limit));
    }
}
