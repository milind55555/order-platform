package com.orderplatform.common.events;

import java.math.BigDecimal;

/** Published by inventory-service once stock is reserved; carries the server-side computed total. */
public record InventoryReservedEvent(Long orderId, String username, BigDecimal totalAmount) {
}
