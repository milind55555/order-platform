package com.orderplatform.common.events;

public record PaymentFailedEvent(Long orderId, String reason) {
}
