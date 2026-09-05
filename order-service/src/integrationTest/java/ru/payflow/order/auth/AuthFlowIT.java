package ru.payflow.order.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import ru.payflow.order.PostgresIT;
import ru.payflow.order.auth.dto.LoginRequest;
import ru.payflow.order.auth.dto.RegisterRequest;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIT extends PostgresIT {

    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JwtDecoder decoder;

    @Test
    void registeredUserLogsInAndGetsUsableToken() throws Exception {
        String email = email();
        String userId = json.readTree(mockMvc.perform(register(email, PASSWORD))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.role").value("USER"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        JsonNode tokens = json.readTree(mockMvc.perform(login(email, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(decoder.decode(tokens.get("accessToken").asText()).getSubject())
                .isEqualTo(userId);
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(900);
    }

    @Test
    void secondRegistrationOfSameEmailIsRejected() throws Exception {
        String email = email();
        mockMvc.perform(register(email, PASSWORD)).andExpect(status().isCreated());

        mockMvc.perform(register(email.toUpperCase(Locale.ROOT), PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Адрес занят"));
    }

    @Test
    void wrongPasswordIsRejectedWithProblemDetail() throws Exception {
        String email = email();
        mockMvc.perform(register(email, PASSWORD)).andExpect(status().isCreated());

        mockMvc.perform(login(email, "wrong password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Не аутентифицирован"));
    }

    @Test
    void shortPasswordDoesNotReachTheDatabase() throws Exception {
        mockMvc.perform(register(email(), "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void refreshReturnsNewPairAndAccessTokenIsNotAccepted() throws Exception {
        String email = email();
        mockMvc.perform(register(email, PASSWORD)).andExpect(status().isCreated());
        JsonNode tokens = json.readTree(mockMvc.perform(login(email, PASSWORD))
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}"
                                .formatted(tokens.get("refreshToken").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}"
                                .formatted(tokens.get("accessToken").asText())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void orderEndpointsAreClosedWithoutIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Не аутентифицирован"));

        mockMvc.perform(get("/api/v1/orders").header("X-Customer-Id", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    private static String email() {
        return "user-" + UUID.randomUUID() + "@payflow.ru";
    }

    private RequestBuilder register(String email, String password) throws Exception {
        return post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest(email, password)));
    }

    private RequestBuilder login(String email, String password) throws Exception {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, password)));
    }
}
