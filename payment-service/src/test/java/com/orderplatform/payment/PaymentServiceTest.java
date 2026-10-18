package com.orderplatform.payment;

import com.orderplatform.common.events.InventoryReservedEvent;
import com.orderplatform.common.events.PaymentCompletedEvent;
import com.orderplatform.common.events.PaymentFailedEvent;
import com.orderplatform.common.events.Topics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private EventPublisher eventPublisher;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, eventPublisher, new BigDecimal("100000"));
    }

    private void stubSave() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });
    }

    @Test
    void paymentWithinLimitIsCompletedAndEventPublished() {
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        stubSave();

        Payment payment = paymentService.process(new InventoryReservedEvent(1L, "alice", new BigDecimal("2500.00")));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishAfterCommit(eq(Topics.PAYMENT_COMPLETED), eq("1"), captor.capture());
        assertThat(((PaymentCompletedEvent) captor.getValue()).paymentId()).isEqualTo(99L);
    }

    @Test
    void paymentAboveLimitIsDeclinedAndFailureEventPublished() {
        when(paymentRepository.findByOrderId(2L)).thenReturn(Optional.empty());
        stubSave();

        Payment payment = paymentService.process(new InventoryReservedEvent(2L, "alice", new BigDecimal("150000.00")));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishAfterCommit(eq(Topics.PAYMENT_FAILED), eq("2"), captor.capture());
        assertThat(((PaymentFailedEvent) captor.getValue()).reason()).contains("limit");
    }

    @Test
    void duplicateEventDoesNotChargeTwice() {
        Payment existing = new Payment();
        existing.setOrderId(3L);
        existing.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findByOrderId(3L)).thenReturn(Optional.of(existing));

        Payment result = paymentService.process(new InventoryReservedEvent(3L, "alice", BigDecimal.TEN));

        assertThat(result).isSameAs(existing);
        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishAfterCommit(any(), any(), any());
    }
}
