package com.orderplatform.order;

import com.orderplatform.common.events.InventoryFailedEvent;
import com.orderplatform.common.events.InventoryReservedEvent;
import com.orderplatform.common.events.OrderCreatedEvent;
import com.orderplatform.common.events.OrderItemEvent;
import com.orderplatform.common.events.PaymentCompletedEvent;
import com.orderplatform.common.events.PaymentFailedEvent;
import com.orderplatform.common.events.Topics;
import com.orderplatform.order.OrderDtos.CreateOrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Order lifecycle / saga orchestration (choreography style):
 * PENDING -> INVENTORY_RESERVED -> CONFIRMED, or CANCELLED when stock or payment fails.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, EventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order create(String username, CreateOrderRequest request) {
        Order order = new Order();
        order.setUsername(username);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(order.getCreatedAt());
        request.items().forEach(i -> order.getItems().add(new OrderLine(i.productId(), i.quantity())));

        Order saved = orderRepository.save(order);

        List<OrderItemEvent> items = saved.getItems().stream()
                .map(i -> new OrderItemEvent(i.getProductId(), i.getQuantity()))
                .toList();
        eventPublisher.publishAfterCommit(Topics.ORDER_CREATED, String.valueOf(saved.getId()),
                new OrderCreatedEvent(saved.getId(), username, items));
        log.info("Order {} created for {}", saved.getId(), username);
        return saved;
    }

    @Transactional(readOnly = true)
    public Order getForUser(Long id, String username, boolean admin) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!admin && !order.getUsername().equals(username)) {
            // do not leak existence of other users' orders
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> listForUser(String username) {
        return orderRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    @Transactional
    public void onInventoryReserved(InventoryReservedEvent event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.INVENTORY_RESERVED);
                order.setTotalAmount(event.totalAmount());
                order.setUpdatedAt(Instant.now());
            }
        });
    }

    @Transactional
    public void onInventoryFailed(InventoryFailedEvent event) {
        cancel(event.orderId(), "Inventory: " + event.reason());
    }

    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.CANCELLED) {
                order.setStatus(OrderStatus.CONFIRMED);
                order.setTotalAmount(event.amount() == null ? BigDecimal.ZERO : event.amount());
                order.setUpdatedAt(Instant.now());
            }
        });
    }

    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        cancel(event.orderId(), "Payment: " + event.reason());
    }

    private void cancel(Long orderId, String reason) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(OrderStatus.CANCELLED);
            order.setFailureReason(reason);
            order.setUpdatedAt(Instant.now());
            log.info("Order {} cancelled: {}", orderId, reason);
        });
    }
}
