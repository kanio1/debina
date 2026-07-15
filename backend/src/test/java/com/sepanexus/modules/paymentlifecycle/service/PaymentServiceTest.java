package com.sepanexus.modules.paymentlifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentStatus;
import com.sepanexus.modules.paymentlifecycle.ingress.IdempotencyClaim;
import com.sepanexus.modules.paymentlifecycle.ingress.IdempotencyStore;
import com.sepanexus.modules.paymentlifecycle.ingress.RawMessageArchive;
import com.sepanexus.modules.paymentlifecycle.isoadapter.IsoIdentifierLookup;
import com.sepanexus.modules.paymentlifecycle.isoadapter.JsonDirectLineageRecorder;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TenantGucConfigurer tenantGucConfigurer;

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private RawMessageArchive rawMessageArchive;

    @Mock
    private JsonDirectLineageRecorder jsonDirectLineageRecorder;

    @Mock
    private IsoIdentifierLookup isoIdentifierLookup;

    @Mock
    private PaymentCreationWriter paymentCreationWriter;

    @Test
    void submitsOneReceivedPayment() {
        UUID tenantId = UUID.randomUUID();
        SubmitPaymentCommand command = new SubmitPaymentCommand(tenantId, null, "E2E-1",
                new BigDecimal("10.00"), "EUR", "DE89370400440532013000", "FR7630006000011234567890189",
                UUID.randomUUID().toString());
        PaymentService service = new PaymentService(paymentRepository, tenantGucConfigurer, idempotencyStore,
                rawMessageArchive, jsonDirectLineageRecorder, isoIdentifierLookup, paymentCreationWriter);
        when(rawMessageArchive.archive(any(), any(), any(), any())).thenReturn(UUID.randomUUID());
        when(idempotencyStore.claim(any(), any(), any())).thenReturn(IdempotencyClaim.claimed());
        PaymentEntity created = PaymentEntity.received(tenantId, null, "E2E-1", new BigDecimal("10.00"), "EUR",
                "DE89370400440532013000", "FR7630006000011234567890189", java.time.Instant.now());
        when(paymentCreationWriter.create(any(), any(), any(), any(), any(), any(), any())).thenReturn(created);

        PaymentEntity saved = service.submitPayment(command);

        ArgumentCaptor<String> endToEndId = ArgumentCaptor.forClass(String.class);
        verify(paymentCreationWriter).create(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.isNull(),
                endToEndId.capture(), any(), any(), any(), any());
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(endToEndId.getValue()).isEqualTo("E2E-1");
    }
}
