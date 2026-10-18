package com.orderplatform.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record ItemRequest(@NotNull Long productId, @Min(1) int quantity) {
    }

    public record CreateOrderRequest(@NotEmpty List<@Valid ItemRequest> items) {
    }

    public record OrderResponse(Long id, String username, OrderStatus status, BigDecimal totalAmount,
                                String failureReason, List<ItemRequest> items, Instant createdAt) {

        public static OrderResponse from(Order order) {
            List<ItemRequest> items = order.getItems().stream()
                    .map(i -> new ItemRequest(i.getProductId(), i.getQuantity()))
                    .toList();
            return new OrderResponse(order.getId(), order.getUsername(), order.getStatus(), order.getTotalAmount(),
                    order.getFailureReason(), items, order.getCreatedAt());
        }
    }
}
