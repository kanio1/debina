package com.sepanexus.modules.paymentlifecycle.web;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.service.PaymentService;
import com.sepanexus.modules.paymentlifecycle.service.SubmitPaymentCommand;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Void> submit(@Valid @RequestBody SubmitPaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        PaymentEntity payment = paymentService.submitPayment(new SubmitPaymentCommand(
                UUID.fromString(jwt.getClaimAsString("tenant_id")),
                request.endToEndId(), request.amount(), request.currency(), request.debtorIban(),
                request.creditorIban()));
        return ResponseEntity.created(URI.create("/api/v1/payments/" + payment.getId())).build();
    }
}
