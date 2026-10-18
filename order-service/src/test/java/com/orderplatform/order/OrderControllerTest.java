package com.orderplatform.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    void createReturns201WithPendingOrder() throws Exception {
        Order saved = new Order();
        saved.setId(1L);
        saved.setUsername("alice");
        saved.setStatus(OrderStatus.PENDING);
        saved.setCreatedAt(Instant.now());
        saved.setItems(new ArrayList<>());
        when(orderService.create(eq("alice"), any())).thenReturn(saved);

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Name", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createRejectsEmptyItems() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("X-User-Name", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsZeroQuantity() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("X-User-Name", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":0}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingIdentityHeaderIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());
    }
}
