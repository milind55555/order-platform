package com.orderplatform.inventory;

import com.orderplatform.common.events.InventoryReservedEvent;
import com.orderplatform.common.events.OrderCreatedEvent;
import com.orderplatform.common.events.OrderItemEvent;
import com.orderplatform.common.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Reserves and releases stock as part of the order saga. */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository productRepository;
    private final StockReservationRepository reservationRepository;
    private final EventPublisher eventPublisher;

    public InventoryService(ProductRepository productRepository,
                            StockReservationRepository reservationRepository,
                            EventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * All-or-nothing: if any line cannot be reserved an {@link InventoryException} is thrown and the whole
     * transaction (including earlier decrements) rolls back. Re-delivered events are ignored (idempotent).
     */
    @Transactional
    @CacheEvict(value = ProductService.CACHE, allEntries = true)
    public void reserve(OrderCreatedEvent event) {
        if (reservationRepository.existsByOrderId(event.orderId())) {
            log.info("Order {} already has a reservation - ignoring duplicate event", event.orderId());
            return;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemEvent item : event.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new InventoryException("Unknown product " + item.productId()));
            if (productRepository.decrementStock(product.getId(), item.quantity()) == 0) {
                throw new InventoryException("Insufficient stock for " + product.getSku());
            }
            reservationRepository.save(new StockReservation(event.orderId(), product.getId(), item.quantity()));
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }

        eventPublisher.publishAfterCommit(Topics.INVENTORY_RESERVED, String.valueOf(event.orderId()),
                new InventoryReservedEvent(event.orderId(), event.username(), total));
        log.info("Reserved stock for order {} (total {})", event.orderId(), total);
    }

    /** Compensation step: give the reserved stock back (e.g. after a payment failure). */
    @Transactional
    @CacheEvict(value = ProductService.CACHE, allEntries = true)
    public void release(Long orderId) {
        List<StockReservation> reservations = reservationRepository.findByOrderIdAndReleasedFalse(orderId);
        for (StockReservation reservation : reservations) {
            productRepository.incrementStock(reservation.getProductId(), reservation.getQuantity());
            reservation.setReleased(true);
        }
        if (!reservations.isEmpty()) {
            log.info("Released stock for order {}", orderId);
        }
    }
}
