package com.orderplatform.payment;

import com.orderplatform.common.events.InventoryReservedEvent;
import com.orderplatform.common.events.PaymentCompletedEvent;
import com.orderplatform.common.events.PaymentFailedEvent;
import com.orderplatform.common.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Simulated payment gateway: payments above the configured limit are declined, which lets you
 * demo the saga compensation path (stock is released and the order is cancelled).
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final EventPublisher eventPublisher;
    private final BigDecimal maxAmount;

    public PaymentService(PaymentRepository paymentRepository, EventPublisher eventPublisher,
                          @Value("${payment.max-amount:100000}") BigDecimal maxAmount) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.maxAmount = maxAmount;
    }

    @Transactional
    public Payment process(InventoryReservedEvent event) {
        var existing = paymentRepository.findByOrderId(event.orderId());
        if (existing.isPresent()) {
            log.info("Payment for order {} already processed - ignoring duplicate event", event.orderId());
            return existing.get();
        }

        Payment payment = new Payment();
        payment.setOrderId(event.orderId());
        payment.setAmount(event.totalAmount());
        payment.setCreatedAt(Instant.now());

        String key = String.valueOf(event.orderId());
        if (event.totalAmount().compareTo(maxAmount) > 0) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Amount exceeds the allowed limit of " + maxAmount);
            Payment saved = paymentRepository.save(payment);
            eventPublisher.publishAfterCommit(Topics.PAYMENT_FAILED, key,
                    new PaymentFailedEvent(event.orderId(), saved.getFailureReason()));
            log.warn("Payment declined for order {}", event.orderId());
            return saved;
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        Payment saved = paymentRepository.save(payment);
        eventPublisher.publishAfterCommit(Topics.PAYMENT_COMPLETED, key,
                new PaymentCompletedEvent(event.orderId(), saved.getId(), saved.getAmount()));
        log.info("Payment completed for order {}", event.orderId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Payment getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }
}
