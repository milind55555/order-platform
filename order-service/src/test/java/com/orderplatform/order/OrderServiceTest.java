package com.orderplatform.order;

import com.orderplatform.common.events.InventoryFailedEvent;
import com.orderplatform.common.events.InventoryReservedEvent;
import com.orderplatform.common.events.OrderCreatedEvent;
import com.orderplatform.common.events.PaymentCompletedEvent;
import com.orderplatform.common.events.PaymentFailedEvent;
import com.orderplatform.common.events.Topics;
import com.orderplatform.order.OrderDtos.CreateOrderRequest;
import com.orderplatform.order.OrderDtos.ItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(7L);
        order.setUsername("alice");
        order.setStatus(status);
        return order;
    }

    @Test
    void createPersistsPendingOrderAndPublishesEvent() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(42L);
            return o;
        });

        Order created = orderService.create("alice",
                new CreateOrderRequest(List.of(new ItemRequest(1L, 2), new ItemRequest(2L, 1))));

        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(created.getItems()).hasSize(2);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishAfterCommit(eq(Topics.ORDER_CREATED), eq("42"), captor.capture());
        OrderCreatedEvent event = (OrderCreatedEvent) captor.getValue();
        assertThat(event.orderId()).isEqualTo(42L);
        assertThat(event.username()).isEqualTo("alice");
        assertThat(event.items()).hasSize(2);
    }

    @Test
    void inventoryReservedMovesOrderForwardAndStoresTotal() {
        Order order = order(OrderStatus.PENDING);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        orderService.onInventoryReserved(new InventoryReservedEvent(7L, "alice", new BigDecimal("150.00")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void paymentCompletedConfirmsOrder() {
        Order order = order(OrderStatus.INVENTORY_RESERVED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        orderService.onPaymentCompleted(new PaymentCompletedEvent(7L, 1L, new BigDecimal("150.00")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void paymentFailedCancelsOrderWithReason() {
        Order order = order(OrderStatus.INVENTORY_RESERVED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        orderService.onPaymentFailed(new PaymentFailedEvent(7L, "limit exceeded"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getFailureReason()).contains("limit exceeded");
    }

    @Test
    void inventoryFailedCancelsOrder() {
        Order order = order(OrderStatus.PENDING);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        orderService.onInventoryFailed(new InventoryFailedEvent(7L, "Insufficient stock"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelledOrderIsNeverConfirmedByLateEvent() {
        Order order = order(OrderStatus.CANCELLED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        orderService.onPaymentCompleted(new PaymentCompletedEvent(7L, 1L, BigDecimal.TEN));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void usersCannotReadOthersOrders() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order(OrderStatus.PENDING)));

        assertThatThrownBy(() -> orderService.getForUser(7L, "mallory", false))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(orderService.getForUser(7L, "mallory", true).getId()).isEqualTo(7L);
    }
}
