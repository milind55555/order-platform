package com.orderplatform.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.common.events.InventoryFailedEvent;
import com.orderplatform.common.events.OrderCreatedEvent;
import com.orderplatform.common.events.PaymentFailedEvent;
import com.orderplatform.common.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final InventoryService inventoryService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public InventoryEventListener(InventoryService inventoryService, EventPublisher eventPublisher,
                                  ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED)
    public void onOrderCreated(String payload) throws JsonProcessingException {
        OrderCreatedEvent event = objectMapper.readValue(payload, OrderCreatedEvent.class);
        try {
            inventoryService.reserve(event);
        } catch (InventoryException ex) {
            log.warn("Reservation failed for order {}: {}", event.orderId(), ex.getMessage());
            eventPublisher.publish(Topics.INVENTORY_FAILED, String.valueOf(event.orderId()),
                    new InventoryFailedEvent(event.orderId(), ex.getMessage()));
        }
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    public void onPaymentFailed(String payload) throws JsonProcessingException {
        PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
        inventoryService.release(event.orderId());
    }
}
