package com.sepanexus.modules.paymentlifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sepanexus.modules.paymentlifecycle.domain.PaymentEntity;
import com.sepanexus.modules.paymentlifecycle.domain.PaymentStatus;
import com.sepanexus.modules.paymentlifecycle.repository.PaymentRepository;
import com.sepanexus.modules.paymentlifecycle.repository.OutboxEventRepository;
import tools.jackson.databind.ObjectMapper;
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
    private OutboxEventRepository outboxEventRepository;

    @Test
    void submitsOneReceivedPayment() {
        UUID tenantId = UUID.randomUUID();
        SubmitPaymentCommand command = new SubmitPaymentCommand(tenantId, "E2E-1",
                new BigDecimal("10.00"), "EUR", "DE89370400440532013000", "FR7630006000011234567890189");
        PaymentService service = new PaymentService(paymentRepository, outboxEventRepository,
                tenantGucConfigurer, new ObjectMapper());
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentEntity saved = service.submitPayment(command);

        ArgumentCaptor<PaymentEntity> payment = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(payment.capture());
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(payment.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(payment.getValue().getEndToEndId()).isEqualTo("E2E-1");
    }
}
