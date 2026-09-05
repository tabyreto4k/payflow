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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void opensAccountAndReadsItBack() throws Exception {
        UUID customerId = UUID.randomUUID();

        String created = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new CreateAccountRequest(customerId, new BigDecimal("100.00")))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        AccountResponse account = json.readValue(created, AccountResponse.class);
        assertThat(account.balance()).isEqualByComparingTo("100.00");

        mockMvc.perform(get("/api/v1/accounts/{id}", account.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.id().toString()))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void unknownAccountAnswersWithProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Ресурс не найден"));
    }

    @Test
    void secondAccountForSameCustomerIsRejected() throws Exception {
        UUID customerId = UUID.randomUUID();
        String body = json.writeValueAsString(new CreateAccountRequest(customerId, BigDecimal.TEN));

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void negativeInitialBalanceIsRejected() throws Exception {
        String body = json.writeValueAsString(new CreateAccountRequest(UUID.randomUUID(), new BigDecimal("-1.00")));

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.initialBalance").exists());
    }
}
