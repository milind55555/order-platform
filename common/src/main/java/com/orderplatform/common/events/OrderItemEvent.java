package com.orderplatform.common.events;

public record OrderItemEvent(Long productId, int quantity) {
}
