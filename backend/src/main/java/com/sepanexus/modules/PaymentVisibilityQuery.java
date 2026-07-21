package com.sepanexus.modules;

import java.util.UUID;

/** Public payment-lifecycle visibility boundary for owner read models. */
public interface PaymentVisibilityQuery {
    void requireVisible(UUID tenantId, UUID branchId, UUID paymentId);
}
