package com.sepanexus.modules.paymentlifecycle.repository;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentApprovalEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Schema-scoped repository; cross-module catalog access remains read-only. */
public interface PaymentApprovalRepository extends JpaRepository<PaymentApprovalEntity, UUID> {

    Optional<PaymentApprovalEntity> findByPaymentId(UUID paymentId);
}
