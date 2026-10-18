package com.orderplatform.common.events;

import java.util.List;

/** Published by order-service when a new order is accepted. */
public record OrderCreatedEvent(Long orderId, String username, List<OrderItemEvent> items) {
}
