package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.ingress.IdempotencyClaim;
import com.sepanexus.modules.paymentlifecycle.ingress.IdempotencyStore;
import com.sepanexus.modules.paymentlifecycle.ingress.RawMessageArchive;
import com.sepanexus.modules.paymentlifecycle.isoadapter.IsoIdentifierLookup;
import com.sepanexus.modules.paymentlifecycle.isoadapter.JsonDirectLineageRecorder;
import com.sepanexus.modules.paymentlifecycle.isoadapter.MissingPrimaryIdentifierException;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final int SUBMIT_RESPONSE_CODE = 201;

    private final PaymentRepository paymentRepository;
    private final TenantGucConfigurer tenantGucConfigurer;
    private final IdempotencyStore idempotencyStore;
    private final RawMessageArchive rawMessageArchive;
    private final JsonDirectLineageRecorder jsonDirectLineageRecorder;
    private final IsoIdentifierLookup isoIdentifierLookup;
    private final PaymentCreationWriter paymentCreationWriter;
    private final PaymentTimelineLookup paymentTimelineLookup;

    public PaymentService(PaymentRepository paymentRepository, TenantGucConfigurer tenantGucConfigurer,
            IdempotencyStore idempotencyStore, RawMessageArchive rawMessageArchive,
            JsonDirectLineageRecorder jsonDirectLineageRecorder, IsoIdentifierLookup isoIdentifierLookup,
            PaymentCreationWriter paymentCreationWriter, PaymentTimelineLookup paymentTimelineLookup) {
        this.paymentRepository = paymentRepository;
        this.tenantGucConfigurer = tenantGucConfigurer;
        this.idempotencyStore = idempotencyStore;
        this.rawMessageArchive = rawMessageArchive;
        this.jsonDirectLineageRecorder = jsonDirectLineageRecorder;
        this.isoIdentifierLookup = isoIdentifierLookup;
        this.paymentCreationWriter = paymentCreationWriter;
        this.paymentTimelineLookup = paymentTimelineLookup;
    }

    /**
     * EPIC-21 Story 21.2: {@code EndToEndId} is an ISO lineage/correlation identifier
     * ({@code iso.payment_iso_identifiers}), not a payment uniqueness key — the blueprint's v2
     * patch (sepa-nexus-message-flow-and-data-blueprint.md §4.3) explicitly removes the old
     * {@code (tenant_id, end_to_end_id)} unique index with no replacement; a payment is found by
     * any ISO id it ever carried, never deduplicated by one. Exactly-once submission is
     * {@code Idempotency-Key}'s job alone (below) — two distinct requests that happen to reuse the
     * same business {@code EndToEndId} are two distinct payments.
     */
    @Transactional
    @PreAuthorize("hasRole('payment_submitter')")
    public PaymentEntity submitPayment(SubmitPaymentCommand command) {
        UUID tenantId = command.tenantId();
        tenantGucConfigurer.apply(tenantId, command.branchId());

        byte[] requestPayload = canonicalRequestBytes(command);
        UUID rawMessageId = rawMessageArchive.archive("REST_JSON", tenantId,
                JsonDirectLineageRecorder.MESSAGE_TYPE, requestPayload);

        IdempotencyClaim claim = idempotencyStore.claim(tenantId, command.idempotencyKey(), sha256(requestPayload));
        if (claim.outcome() == IdempotencyClaim.Outcome.CONFLICT) {
            throw new IdempotencyConflictException(command.idempotencyKey());
        }
        if (claim.outcome() == IdempotencyClaim.Outcome.REPLAY) {
            return paymentRepository.findById(claim.existingPaymentId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency replay points at a payment that no longer exists: " + claim.existingPaymentId()));
        }

        PaymentEntity payment = paymentCreationWriter.create(tenantId, command.branchId(),
                command.amount(), command.currency(), command.debtorIban(), command.creditorIban());
        jsonDirectLineageRecorder.record(payment.getId(), rawMessageId, command.endToEndId());
        idempotencyStore.complete(tenantId, command.idempotencyKey(), payment.getId(), SUBMIT_RESPONSE_CODE);

        return payment;
    }

    private byte[] canonicalRequestBytes(SubmitPaymentCommand command) {
        String canonical = "%s|%s|%s|%s|%s".formatted(command.endToEndId(), command.amount(), command.currency(),
                command.debtorIban(), command.creditorIban());
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final String PAYMENT_READ_ROLES =
            "hasAnyRole('payment_viewer','payment_submitter','payment_approver','operator','auditor')";

    @Transactional(readOnly = true)
    @PreAuthorize(PAYMENT_READ_ROLES)
    public List<PaymentSummary> visiblePayments(String tenantIdClaim) {
        tenantGucConfigurer.apply(tenantIdClaim == null ? null : UUID.fromString(tenantIdClaim));
        List<PaymentEntity> payments = paymentRepository.findAll();
        Map<UUID, String> primaryEndToEndIds = isoIdentifierLookup.findPrimaryEndToEndIds(
                payments.stream().map(PaymentEntity::getId).toList());
        return payments.stream()
                .map(payment -> new PaymentSummary(payment, requirePrimary(primaryEndToEndIds, payment.getId())))
                .toList();
    }

    private static String requirePrimary(Map<UUID, String> primaryEndToEndIds, UUID paymentId) {
        String endToEndId = primaryEndToEndIds.get(paymentId);
        if (endToEndId == null) {
            throw new MissingPrimaryIdentifierException(paymentId);
        }
        return endToEndId;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PAYMENT_READ_ROLES)
    public PaymentDetail paymentDetail(String tenantIdClaim, UUID paymentId) {
        tenantGucConfigurer.apply(tenantIdClaim == null ? null : UUID.fromString(tenantIdClaim));
        // RLS on payment.payments already scopes this lookup to the caller's tenant (and branch, if
        // set) — a payment belonging to another tenant is indistinguishable from a non-existent one.
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        String endToEndId = isoIdentifierLookup.findPrimaryEndToEndId(paymentId);
        return new PaymentDetail(payment, endToEndId, isoIdentifierLookup.findByPaymentId(paymentId));
    }

    private static final int DEFAULT_TIMELINE_LIMIT = 50;
    private static final int MAX_TIMELINE_LIMIT = 200;

    /**
     * OQ-12 / EPIC-24 Story 24.2: {@code seq} cursor, never offset — a payment's timeline can grow
     * (future R-message/recall/return lineage), and offset pagination would skip or repeat rows if
     * new entries land while a caller pages through. Tenant/branch visibility is enforced by
     * resolving {@code paymentId} through the same RLS-protected {@link #paymentDetail} read path
     * first — {@code payment_status_history} itself carries no tenant/branch column to filter on
     * (matches the source DDL, sepa-nexus-message-flow-and-data-blueprint.md §4.3).
     */
    @Transactional(readOnly = true)
    @PreAuthorize(PAYMENT_READ_ROLES)
    public PaymentTimelinePage paymentTimeline(String tenantIdClaim, UUID paymentId, Integer limit, Integer afterSeq) {
        tenantGucConfigurer.apply(tenantIdClaim == null ? null : UUID.fromString(tenantIdClaim));
        paymentRepository.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));

        int effectiveLimit = clampLimit(limit);
        int effectiveAfterSeq = afterSeq == null ? 0 : afterSeq;
        // Fetch one extra row beyond the page size: whether a page is exactly full because more
        // rows exist, or exactly full because it's the last page, are indistinguishable otherwise.
        List<PaymentTimelineLookup.TimelineEntry> fetched =
                paymentTimelineLookup.find(paymentId, effectiveLimit + 1, effectiveAfterSeq);
        boolean hasMore = fetched.size() > effectiveLimit;
        List<PaymentTimelineLookup.TimelineEntry> entries = hasMore ? fetched.subList(0, effectiveLimit) : fetched;
        Integer nextAfterSeq = hasMore ? entries.get(entries.size() - 1).seq() : null;
        return new PaymentTimelinePage(entries, nextAfterSeq);
    }

    private static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_TIMELINE_LIMIT;
        }
        return Math.min(requested, MAX_TIMELINE_LIMIT);
    }

    public record PaymentSummary(PaymentEntity payment, String endToEndId) {
    }

    public record PaymentDetail(PaymentEntity payment, String endToEndId,
            List<IsoIdentifierLookup.IsoIdentifierView> isoIdentifiers) {
    }

    public record PaymentTimelinePage(List<PaymentTimelineLookup.TimelineEntry> items, Integer nextAfterSeq) {
    }
}
