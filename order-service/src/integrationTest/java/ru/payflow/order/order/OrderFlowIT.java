package ru.payflow.order.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import ru.payflow.order.PostgresIT;
import ru.payflow.order.order.dto.CreateOrderRequest;
import ru.payflow.order.order.dto.OrderItemRequest;
import ru.payflow.order.order.dto.OrderResponse;

@SpringBootTest
@AutoConfigureMockMvc
class OrderFlowIT extends PostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void createdOrderWaitsForPayment() throws Exception {
        // Счета уехали в payment-service, Kafka ещё нет — исход оплаты заказу принести некому.
        mockMvc.perform(createOrder(customer(), "40.00", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.total").value(40.00))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void ordersAreListedPerCustomer() throws Exception {
        UUID customer = customer();
        UUID stranger = customer();
        mockMvc.perform(createOrder(customer, "10.00", 1)).andExpect(status().isCreated());
        mockMvc.perform(createOrder(customer, "20.00", 1)).andExpect(status().isCreated());
        mockMvc.perform(createOrder(stranger, "30.00", 1)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/orders").header("X-Customer-Id", customer).param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void strangersOrderIsInvisible() throws Exception {
        UUID customer = customer();
        UUID orderId = orderId(mockMvc.perform(createOrder(customer, "10.00", 1))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header("X-Customer-Id", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void awaitingOrderIsCancelled() throws Exception {
        UUID customer = customer();
        UUID orderId = orderId(mockMvc.perform(createOrder(customer, "10.00", 1))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId).header("X-Customer-Id", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void orderWithoutItemsIsRejected() throws Exception {
        UUID customer = customer();

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Customer-Id", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateOrderRequest(List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").exists());
    }

    private UUID customer() {
        return registerCustomer();
    }

    private RequestBuilder createOrder(UUID customer, String price, int quantity) throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(UUID.randomUUID(), quantity, new BigDecimal(price))));
        return post("/api/v1/orders")
                .header("X-Customer-Id", customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request));
    }

    private UUID orderId(String body) throws Exception {
        return json.readValue(body, OrderResponse.class).id();
    }
}
