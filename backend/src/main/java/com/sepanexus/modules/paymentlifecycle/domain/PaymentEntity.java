package com.sepanexus.modules.paymentlifecycle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", schema = "payment")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", updatable = false)
    private UUID branchId;

    @Column(name = "end_to_end_id", nullable = false, updatable = false)
    private String endToEndId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "debtor_iban", nullable = false)
    private String debtorIban;

    @Column(name = "creditor_iban", nullable = false)
    private String creditorIban;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PaymentEntity() {
    }

    private PaymentEntity(UUID tenantId, UUID branchId, String endToEndId, BigDecimal amount, String currency,
            String debtorIban, String creditorIban, Instant createdAt) {
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.endToEndId = endToEndId;
        this.amount = amount;
        this.currency = currency;
        this.debtorIban = debtorIban;
        this.creditorIban = creditorIban;
        this.status = PaymentStatus.RECEIVED;
        this.createdAt = createdAt;
    }

    public static PaymentEntity received(UUID tenantId, UUID branchId, String endToEndId, BigDecimal amount,
            String currency, String debtorIban, String creditorIban, Instant createdAt) {
        return new PaymentEntity(tenantId, branchId, endToEndId, amount, currency, debtorIban, creditorIban, createdAt);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public String getEndToEndId() { return endToEndId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDebtorIban() { return debtorIban; }
    public String getCreditorIban() { return creditorIban; }
    public PaymentStatus getStatus() { return status; }

    public void markValidated() {
        PaymentTransitionTable.requireLegal(status, PaymentStatus.VALIDATED);
        status = PaymentStatus.VALIDATED;
    }
}
