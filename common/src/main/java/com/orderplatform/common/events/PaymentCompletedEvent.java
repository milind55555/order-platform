package com.orderplatform.common.events;

import java.math.BigDecimal;

public record PaymentCompletedEvent(Long orderId, Long paymentId, BigDecimal amount) {
}
