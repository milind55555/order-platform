package com.orderplatform.order;

public enum OrderStatus {
    /** Order accepted, waiting for stock reservation. */
    PENDING,
    /** Stock reserved, waiting for payment. */
    INVENTORY_RESERVED,
    /** Payment captured - order complete. */
    CONFIRMED,
    /** Stock or payment step failed - saga compensated. */
    CANCELLED
}
