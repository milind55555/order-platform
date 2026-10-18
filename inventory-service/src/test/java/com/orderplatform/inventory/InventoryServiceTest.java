package com.orderplatform.inventory;

import com.orderplatform.common.events.InventoryReservedEvent;
import com.orderplatform.common.events.OrderCreatedEvent;
import com.orderplatform.common.events.OrderItemEvent;
import com.orderplatform.common.events.Topics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockReservationRepository reservationRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private InventoryService inventoryService;

    private Product product(Long id, String sku, String price) {
        Product p = new Product(sku, sku, new BigDecimal(price), 10);
        p.setId(id);
        return p;
    }

    @Test
    void reserveDecrementsStockAndPublishesTotal() {
        when(reservationRepository.existsByOrderId(1L)).thenReturn(false);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product(10L, "A", "100.00")));
        when(productRepository.findById(11L)).thenReturn(Optional.of(product(11L, "B", "50.50")));
        when(productRepository.decrementStock(10L, 2)).thenReturn(1);
        when(productRepository.decrementStock(11L, 1)).thenReturn(1);

        inventoryService.reserve(new OrderCreatedEvent(1L, "alice",
                List.of(new OrderItemEvent(10L, 2), new OrderItemEvent(11L, 1))));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishAfterCommit(eq(Topics.INVENTORY_RESERVED), eq("1"), captor.capture());
        InventoryReservedEvent published = (InventoryReservedEvent) captor.getValue();
        assertThat(published.totalAmount()).isEqualByComparingTo("250.50");
        verify(reservationRepository, org.mockito.Mockito.times(2)).save(any(StockReservation.class));
    }

    @Test
    void reserveFailsWhenStockIsInsufficient() {
        when(reservationRepository.existsByOrderId(2L)).thenReturn(false);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product(10L, "A", "100.00")));
        when(productRepository.decrementStock(10L, 999)).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.reserve(
                new OrderCreatedEvent(2L, "alice", List.of(new OrderItemEvent(10L, 999)))))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("Insufficient stock");
        verify(eventPublisher, never()).publishAfterCommit(any(), any(), any());
    }

    @Test
    void reserveFailsForUnknownProduct() {
        when(reservationRepository.existsByOrderId(3L)).thenReturn(false);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.reserve(
                new OrderCreatedEvent(3L, "alice", List.of(new OrderItemEvent(99L, 1)))))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("Unknown product");
    }

    @Test
    void duplicateEventIsIgnored() {
        when(reservationRepository.existsByOrderId(4L)).thenReturn(true);

        inventoryService.reserve(new OrderCreatedEvent(4L, "alice", List.of(new OrderItemEvent(10L, 1))));

        verify(productRepository, never()).decrementStock(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(eventPublisher, never()).publishAfterCommit(any(), any(), any());
    }

    @Test
    void releaseReturnsStockAndMarksReservationsReleased() {
        StockReservation r1 = new StockReservation(5L, 10L, 2);
        StockReservation r2 = new StockReservation(5L, 11L, 1);
        when(reservationRepository.findByOrderIdAndReleasedFalse(5L)).thenReturn(List.of(r1, r2));

        inventoryService.release(5L);

        verify(productRepository).incrementStock(10L, 2);
        verify(productRepository).incrementStock(11L, 1);
        assertThat(r1.isReleased()).isTrue();
        assertThat(r2.isReleased()).isTrue();
    }
}
