package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.PaymentVisibilityQuery;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies payment RLS before an owner read model may disclose ISO metadata. */
@Service
class PaymentVisibilityReadModel implements PaymentVisibilityQuery {
    private final PaymentRepository paymentRepository;
    private final TenantGucConfigurer tenantGucConfigurer;

    PaymentVisibilityReadModel(PaymentRepository paymentRepository, TenantGucConfigurer tenantGucConfigurer) {
        this.paymentRepository = paymentRepository;
        this.tenantGucConfigurer = tenantGucConfigurer;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireVisible(UUID tenantId, UUID branchId, UUID paymentId) {
        tenantGucConfigurer.apply(tenantId, branchId);
        paymentRepository.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
