package com.orderplatform.common.events;

public record InventoryFailedEvent(Long orderId, String reason) {
}
