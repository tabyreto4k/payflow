package ru.payflow.payment.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import ru.payflow.payment.PostgresIT;
import ru.payflow.payment.account.dto.AccountResponse;
import ru.payflow.payment.account.dto.CreateAccountRequest;
import ru.payflow.payment.account.dto.DepositRequest;
import ru.payflow.payment.account.service.AccountService;

@SpringBootTest
@AutoConfigureMockMvc
class DepositIdempotencyIT extends PostgresIT {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AccountService accounts;

    @Test
    void repeatedKeyDepositsOnceAndReturnsTheSameBody() throws Exception {
        UUID accountId = openAccount();
        String key = UUID.randomUUID().toString();

        String first = mockMvc.perform(deposit(accountId, key, HUNDRED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200.00))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String second = mockMvc.perform(deposit(accountId, key, HUNDRED))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("200.00");
    }

    @Test
    void anotherKeyDepositsAgain() throws Exception {
        UUID accountId = openAccount();

        mockMvc.perform(deposit(accountId, UUID.randomUUID().toString(), HUNDRED))
                .andExpect(status().isOk());
        mockMvc.perform(deposit(accountId, UUID.randomUUID().toString(), HUNDRED))
                .andExpect(status().isOk());

        assertThat(balanceOf(accountId)).isEqualByComparingTo("300.00");
    }

    @Test
    void twoRacingRepeatsStillDepositOnce() throws Exception {
        UUID accountId = openAccount();
        String key = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        Callable<AccountResponse> call = () -> {
            start.await();
            return accounts.deposit(accountId, key, new DepositRequest(HUNDRED));
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<AccountResponse> first = pool.submit(call);
            Future<AccountResponse> second = pool.submit(call);
            start.countDown();
            List<AccountResponse> responses =
                    List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertThat(responses.get(0)).isEqualTo(responses.get(1));
        } finally {
            pool.shutdownNow();
        }

        assertThat(balanceOf(accountId)).isEqualByComparingTo("200.00");
    }

    @Test
    void depositWithoutKeyIsRejected() throws Exception {
        UUID accountId = openAccount();

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new DepositRequest(HUNDRED))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void depositToUnknownAccountIsNotFound() throws Exception {
        mockMvc.perform(deposit(UUID.randomUUID(), UUID.randomUUID().toString(), HUNDRED))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonPositiveDepositIsRejected() throws Exception {
        UUID accountId = openAccount();

        mockMvc.perform(deposit(accountId, UUID.randomUUID().toString(), new BigDecimal("-1.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    private UUID openAccount() throws Exception {
        String created = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateAccountRequest(UUID.randomUUID(), HUNDRED))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readValue(created, AccountResponse.class).id();
    }

    private BigDecimal balanceOf(UUID accountId) {
        return accounts.getById(accountId).getBalance();
    }

    private RequestBuilder deposit(UUID accountId, String key, BigDecimal amount) throws Exception {
        return post("/api/v1/accounts/{id}/deposit", accountId)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new DepositRequest(amount)));
    }
}
