package com.orderplatform.inventory;

import java.io.Serializable;
import java.math.BigDecimal;

/** Serializable because instances are cached in Redis. */
public record ProductDto(Long id, String sku, String name, BigDecimal price, int availableQuantity)
        implements Serializable {

    public static ProductDto from(Product p) {
        return new ProductDto(p.getId(), p.getSku(), p.getName(), p.getPrice(), p.getAvailableQuantity());
    }
}
