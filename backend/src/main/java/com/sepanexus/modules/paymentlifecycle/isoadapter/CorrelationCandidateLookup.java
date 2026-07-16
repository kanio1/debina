package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.List;
import java.util.UUID;

/**
 * EPIC-27 Story 27.2B: the read port {@link Pacs002CorrelationPolicy} runs each ordered match
 * strategy through. Every method is tenant-scoped (the readiness audit's tenant-scoping decision —
 * {@code iso.iso_messages.tenant_id}, backfilled from {@code ingress.raw_inbound_messages}) and
 * stays entirely inside {@code iso-adapter}'s own schema: no method here may read {@code payment.*}
 * directly (root {@code AGENTS.md}/§3.6.3 binding rule). The Story 27.2C persistence adapter is the
 * only production implementation; {@link Pacs002CorrelationPolicy}'s own tests use a fake.
 */
public interface CorrelationCandidateLookup {

    /** Step 1: {@code OrgnlMsgId+OrgnlTxId}. */
    List<CorrelationCandidate> findByOrgnlMsgIdAndOrgnlTxId(UUID tenantId, String orgnlMsgId, String orgnlTxId);

    /** Step 2: {@code OrgnlMsgId+OrgnlInstrId+OrgnlEndToEndId}. */
    List<CorrelationCandidate> findByOrgnlMsgIdAndOrgnlInstrIdAndOrgnlEndToEndId(
            UUID tenantId, String orgnlMsgId, String orgnlInstrId, String orgnlEndToEndId);

    /** Step 3: {@code UETR}. */
    List<CorrelationCandidate> findByUetr(UUID tenantId, String uetr);

    /** Step 4: {@code OrgnlMsgId+OrgnlEndToEndId}. */
    List<CorrelationCandidate> findByOrgnlMsgIdAndOrgnlEndToEndId(UUID tenantId, String orgnlMsgId, String orgnlEndToEndId);
}
