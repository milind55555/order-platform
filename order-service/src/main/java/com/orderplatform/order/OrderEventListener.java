package com.orderplatform.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.common.events.InventoryFailedEvent;
import com.orderplatform.common.events.InventoryReservedEvent;
import com.orderplatform.common.events.PaymentCompletedEvent;
import com.orderplatform.common.events.PaymentFailedEvent;
import com.orderplatform.common.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes saga events from inventory-service and payment-service. */
@Component
public class OrderEventListener {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public OrderEventListener(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.INVENTORY_RESERVED)
    public void onInventoryReserved(String payload) throws JsonProcessingException {
        orderService.onInventoryReserved(objectMapper.readValue(payload, InventoryReservedEvent.class));
    }

    @KafkaListener(topics = Topics.INVENTORY_FAILED)
    public void onInventoryFailed(String payload) throws JsonProcessingException {
        orderService.onInventoryFailed(objectMapper.readValue(payload, InventoryFailedEvent.class));
    }

    @KafkaListener(topics = Topics.PAYMENT_COMPLETED)
    public void onPaymentCompleted(String payload) throws JsonProcessingException {
        orderService.onPaymentCompleted(objectMapper.readValue(payload, PaymentCompletedEvent.class));
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    public void onPaymentFailed(String payload) throws JsonProcessingException {
        orderService.onPaymentFailed(objectMapper.readValue(payload, PaymentFailedEvent.class));
    }
}
