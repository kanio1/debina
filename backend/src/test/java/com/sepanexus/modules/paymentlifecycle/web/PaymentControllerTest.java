package com.sepanexus.modules.paymentlifecycle.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentStatus;
import com.sepanexus.modules.paymentlifecycle.isoadapter.IsoIdentifierLookup.IsoIdentifierView;
import com.sepanexus.modules.paymentlifecycle.service.PaymentNotFoundException;
import com.sepanexus.modules.paymentlifecycle.service.PaymentService;
import com.sepanexus.modules.paymentlifecycle.service.PaymentService.PaymentDetail;
import com.sepanexus.modules.paymentlifecycle.service.PaymentService.PaymentSummary;
import com.sepanexus.modules.paymentlifecycle.service.PaymentService.PaymentTimelinePage;
import com.sepanexus.modules.paymentlifecycle.service.PaymentTimelineLookup.TimelineEntry;
import com.sepanexus.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void createsPaymentAndReturnsLocation() throws Exception {
        PaymentEntity payment = org.mockito.Mockito.mock(PaymentEntity.class);
        UUID paymentId = UUID.randomUUID();
        when(payment.getId()).thenReturn(paymentId);
        when(paymentService.submitPayment(any())).thenReturn(payment);

        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", UUID.randomUUID().toString()))
                                .authorities(() -> "ROLE_payment_submitter"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(validPaymentJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/payments/" + paymentId));
    }

    @Test
    void listsVisiblePaymentsForAuthenticatedTenant() throws Exception {
        PaymentEntity payment = org.mockito.Mockito.mock(PaymentEntity.class);
        UUID paymentId = UUID.randomUUID();
        when(payment.getId()).thenReturn(paymentId);
        when(payment.getAmount()).thenReturn(new BigDecimal("10.00"));
        when(payment.getCurrency()).thenReturn("EUR");
        when(payment.getStatus()).thenReturn(PaymentStatus.RECEIVED);
        when(paymentService.visiblePayments(any())).thenReturn(List.of(new PaymentSummary(payment, "E2E-1")));

        mockMvc.perform(get("/api/v1/payments")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", UUID.randomUUID().toString()))
                                .authorities(() -> "ROLE_payment_submitter")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(paymentId.toString()))
                .andExpect(jsonPath("$[0].endToEndId").value("E2E-1"))
                .andExpect(jsonPath("$[0].status").value("RECEIVED"));
    }

    @Test
    void returnsPaymentDetailWithIsoIdentifiers() throws Exception {
        PaymentEntity payment = org.mockito.Mockito.mock(PaymentEntity.class);
        UUID paymentId = UUID.randomUUID();
        when(payment.getId()).thenReturn(paymentId);
        when(payment.getAmount()).thenReturn(new BigDecimal("10.00"));
        when(payment.getCurrency()).thenReturn("EUR");
        when(payment.getStatus()).thenReturn(PaymentStatus.RECEIVED);
        when(payment.getDebtorIban()).thenReturn("DE89370400440532013000");
        when(payment.getCreditorIban()).thenReturn("FR7630006000011234567890189");
        UUID isoMessageId = UUID.randomUUID();
        when(paymentService.paymentDetail(any(), org.mockito.ArgumentMatchers.eq(paymentId))).thenReturn(
                new PaymentDetail(payment, "E2E-1", List.of(new IsoIdentifierView("JSON_DIRECT", "E2E-1", isoMessageId))));

        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", UUID.randomUUID().toString()))
                                .authorities(() -> "ROLE_payment_submitter")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.isoIdentifiers[0].sourceMessageType").value("JSON_DIRECT"))
                .andExpect(jsonPath("$.isoIdentifiers[0].isoMessageId").value(isoMessageId.toString()));
    }

    @Test
    void returnsNotFoundForUnknownPayment() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentService.paymentDetail(any(), org.mockito.ArgumentMatchers.eq(paymentId)))
                .thenThrow(new PaymentNotFoundException(paymentId));

        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", UUID.randomUUID().toString()))
                                .authorities(() -> "ROLE_payment_submitter")))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsTimelineOrderedBySeq() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID eventRef = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-15T10:00:00Z");
        List<TimelineEntry> entries = List.of(
                new TimelineEntry(1, null, "RECEIVED", "RECEIVED", null, "INTERNAL", "SYSTEM", false,
                        "payment.submitted.v1", eventRef, now),
                new TimelineEntry(2, "RECEIVED", "VALIDATED", "VALIDATED", null, "INTERNAL", "SYSTEM", false,
                        "payment.submitted.v1", eventRef, now.plusSeconds(5)));
        when(paymentService.paymentTimeline(any(), org.mockito.ArgumentMatchers.eq(paymentId), any(), any()))
                .thenReturn(new PaymentTimelinePage(entries, null));

        mockMvc.perform(get("/api/v1/payments/" + paymentId + "/timeline")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", UUID.randomUUID().toString()))
                                .authorities(() -> "ROLE_payment_viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].seq").value(1))
                .andExpect(jsonPath("$.items[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$.items[0].toStatus").value("RECEIVED"))
                .andExpect(jsonPath("$.items[1].seq").value(2))
                .andExpect(jsonPath("$.items[1].fromStatus").value("RECEIVED"))
                .andExpect(jsonPath("$.items[1].toStatus").value("VALIDATED"))
                .andExpect(jsonPath("$.nextAfterSeq").doesNotExist());
    }

    @Test
    void returnsNotFoundForTimelineOfUnknownPayment() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentService.paymentTimeline(any(), org.mockito.ArgumentMatchers.eq(paymentId), any(), any()))
                .thenThrow(new PaymentNotFoundException(paymentId));

        mockMvc.perform(get("/api/v1/payments/" + paymentId + "/timeline")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", UUID.randomUUID().toString()))
                                .authorities(() -> "ROLE_payment_viewer")))
                .andExpect(status().isNotFound());
    }

    static String validPaymentJson() {
        return """
                {"endToEndId":"E2E-1","amount":10.00,"currency":"EUR",
                 "debtorIban":"DE89370400440532013000","creditorIban":"FR7630006000011234567890189"}
                """;
    }
}
