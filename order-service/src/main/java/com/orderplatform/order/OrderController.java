package com.orderplatform.order;

import com.orderplatform.order.OrderDtos.CreateOrderRequest;
import com.orderplatform.order.OrderDtos.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Identity comes from headers set by the API gateway after JWT validation.
 * The service is only reachable through the gateway (ClusterIP in Kubernetes).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestHeader("X-User-Name") String username,
                                @Valid @RequestBody CreateOrderRequest request) {
        return OrderResponse.from(orderService.create(username, request));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id,
                             @RequestHeader("X-User-Name") String username,
                             @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {
        return OrderResponse.from(orderService.getForUser(id, username, "ADMIN".equals(roles)));
    }

    @GetMapping
    public List<OrderResponse> list(@RequestHeader("X-User-Name") String username) {
        return orderService.listForUser(username).stream().map(OrderResponse::from).toList();
    }
}
