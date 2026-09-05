package ru.payflow.payment.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.payflow.payment.PostgresIT;
import ru.payflow.payment.account.dto.AccountResponse;
import ru.payflow.payment.account.dto.CreateAccountRequest;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIT extends PostgresIT {

    private static final String CUSTOMER_ID = "X-Customer-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void opensAccountAndReadsItBack() throws Exception {
        UUID customerId = UUID.randomUUID();

        String created = mockMvc.perform(post("/api/v1/accounts")
                        .header(CUSTOMER_ID, customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateAccountRequest(new BigDecimal("100.00")))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        AccountResponse account = json.readValue(created, AccountResponse.class);
        assertThat(account.balance()).isEqualByComparingTo("100.00");

        mockMvc.perform(get("/api/v1/accounts/{id}", account.id()).header(CUSTOMER_ID, customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.id().toString()))
                .andExpect(jsonPath("$.balance").value(100.00));

        // Чужой счёт неотличим от несуществующего.
        mockMvc.perform(get("/api/v1/accounts/{id}", account.id()).header(CUSTOMER_ID, UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestWithoutIdentityIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Не аутентифицирован"));
    }

    @Test
    void unknownAccountAnswersWithProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID()).header(CUSTOMER_ID, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Ресурс не найден"));
    }

    @Test
    void secondAccountForSameCustomerIsRejected() throws Exception {
        UUID customerId = UUID.randomUUID();
        String body = json.writeValueAsString(new CreateAccountRequest(BigDecimal.TEN));

        mockMvc.perform(post("/api/v1/accounts")
                        .header(CUSTOMER_ID, customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/accounts")
                        .header(CUSTOMER_ID, customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void negativeInitialBalanceIsRejected() throws Exception {
        String body = json.writeValueAsString(new CreateAccountRequest(new BigDecimal("-1.00")));

        mockMvc.perform(post("/api/v1/accounts")
                        .header(CUSTOMER_ID, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.initialBalance").exists());
    }
}
