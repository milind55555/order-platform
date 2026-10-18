package com.orderplatform.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, Long orderId, BigDecimal amount, PaymentStatus status,
                              String failureReason, Instant createdAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getStatus(),
                p.getFailureReason(), p.getCreatedAt());
    }
}
