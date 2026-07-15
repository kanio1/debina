package com.sepanexus.modules.paymentlifecycle.web;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.service.Pain001IngestionService;
import com.sepanexus.modules.paymentlifecycle.service.Pain001SubmissionCommand;
import com.sepanexus.modules.paymentlifecycle.service.PaymentService;
import com.sepanexus.modules.paymentlifecycle.service.SubmitPaymentCommand;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code PaymentSubmissionController} (sepa-nexus-message-flow-and-data-blueprint.md §3.1) — one
 * controller, two REST channels named there together: {@code /api/v1/payments} (JSON_DIRECT) and
 * {@code /api/v1/iso/pain001} (EPIC-19 Story 19.4, XML). No class-level path prefix, since the two
 * channels don't share one.
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final Pain001IngestionService pain001IngestionService;

    public PaymentController(PaymentService paymentService, Pain001IngestionService pain001IngestionService) {
        this.paymentService = paymentService;
        this.pain001IngestionService = pain001IngestionService;
    }

    @PostMapping("/api/v1/payments")
    public ResponseEntity<Void> submit(@Valid @RequestBody SubmitPaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        PaymentEntity payment = paymentService.submitPayment(new SubmitPaymentCommand(
                UUID.fromString(jwt.getClaimAsString("tenant_id")),
                branchIdClaim(jwt),
                request.endToEndId(), request.amount(), request.currency(), request.debtorIban(),
                request.creditorIban(), idempotencyKey));
        return ResponseEntity.created(URI.create("/api/v1/payments/" + payment.getId())).build();
    }

    /**
     * {@code X-Signer-Id}/{@code X-Signature}/{@code X-Signature-Algo} are this channel's signed-
     * transport headers — the blueprint names the bank-XML channel as signed (§2.1) but does not
     * pin exact header names, so these are a pragmatic {@code [OPEN-QUESTION]} resolution, same
     * discipline as Story 31.2's Ed25519 pick. Absent {@code X-Signature} is a legitimate case (an
     * unsigned submission attempt on a channel that requires one) and must still reach
     * {@code SignaturePort} as evidence, never be rejected earlier by validation.
     */
    @PostMapping(path = "/api/v1/iso/pain001", consumes = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<Void> submitPain001(@RequestBody byte[] xmlBytes,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Signer-Id", required = false) UUID declaredSignerId,
            @RequestHeader(value = "X-Signature", required = false) String signatureBase64,
            @RequestHeader(value = "X-Signature-Algo", defaultValue = "Ed25519") String algo,
            @AuthenticationPrincipal Jwt jwt) {
        byte[] signatureBytes = signatureBase64 == null ? null : Base64.getDecoder().decode(signatureBase64);
        PaymentEntity payment = pain001IngestionService.submit(new Pain001SubmissionCommand(
                UUID.fromString(jwt.getClaimAsString("tenant_id")), branchIdClaim(jwt), xmlBytes, signatureBytes,
                declaredSignerId, algo, idempotencyKey));
        return ResponseEntity.created(URI.create("/api/v1/payments/" + payment.getId())).build();
    }

    private static UUID branchIdClaim(Jwt jwt) {
        String branchId = jwt.getClaimAsString("branch_id");
        return branchId == null ? null : UUID.fromString(branchId);
    }

    @GetMapping("/api/v1/payments")
    public List<PaymentSummaryResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return paymentService.visiblePayments(jwt.getClaimAsString("tenant_id")).stream()
                .map(PaymentSummaryResponse::from)
                .toList();
    }

    @GetMapping("/api/v1/payments/{id}")
    public PaymentDetailResponse detail(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return PaymentDetailResponse.from(paymentService.paymentDetail(jwt.getClaimAsString("tenant_id"), id));
    }
}
