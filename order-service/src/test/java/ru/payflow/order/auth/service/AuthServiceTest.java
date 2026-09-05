package ru.payflow.order.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import ru.payflow.order.auth.dto.LoginRequest;
import ru.payflow.order.auth.dto.RefreshRequest;
import ru.payflow.order.auth.dto.RegisterRequest;
import ru.payflow.order.auth.dto.TokenResponse;
import ru.payflow.order.auth.dto.UserResponse;
import ru.payflow.order.auth.model.Role;
import ru.payflow.order.auth.model.User;
import ru.payflow.order.auth.repository.UserRepository;
import ru.payflow.order.exception.EmailAlreadyUsedException;
import ru.payflow.order.exception.InvalidCredentialsException;
import ru.payflow.order.exception.InvalidTokenException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "correct horse battery";

    @Mock
    private UserRepository users;

    @Mock
    private TokenService tokens;

    private final PasswordEncoder passwords = new BCryptPasswordEncoder(4);

    private AuthService authService() {
        return new AuthService(users, passwords, tokens);
    }

    @Test
    void registrationHashesPasswordAndNormalizesEmail() {
        when(users.existsByEmail("ivan@payflow.ru")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation -> stored(invocation.getArgument(0)));

        UserResponse response = authService().register(new RegisterRequest("  Ivan@PayFlow.RU ", PASSWORD));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ivan@payflow.ru");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, saved.getValue().getPasswordHash()))
                .isTrue();
        assertThat(response.role()).isEqualTo(Role.USER);
    }

    @Test
    void takenEmailIsRejected() {
        when(users.existsByEmail("ivan@payflow.ru")).thenReturn(true);

        assertThatExceptionOfType(EmailAlreadyUsedException.class)
                .isThrownBy(() -> authService().register(new RegisterRequest("ivan@payflow.ru", PASSWORD)));

        verify(users, never()).save(any());
    }

    @Test
    void loginIssuesBothTokens() {
        User user = user();
        when(users.findByEmail("ivan@payflow.ru")).thenReturn(Optional.of(user));
        when(tokens.issueAccess(user)).thenReturn("access");
        when(tokens.issueRefresh(user)).thenReturn("refresh");
        when(tokens.accessTtl()).thenReturn(Duration.ofMinutes(15));

        TokenResponse response = authService().login(new LoginRequest("ivan@payflow.ru", PASSWORD));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void wrongPasswordIsRejected() {
        when(users.findByEmail("ivan@payflow.ru")).thenReturn(Optional.of(user()));

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> authService().login(new LoginRequest("ivan@payflow.ru", "wrong password")));

        verify(tokens, never()).issueAccess(any());
    }

    @Test
    void unknownEmailIsRejected() {
        when(users.findByEmail("nobody@payflow.ru")).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> authService().login(new LoginRequest("nobody@payflow.ru", PASSWORD)));
    }

    @Test
    void refreshIssuesNewPairForTokenOwner() {
        User user = user();
        when(tokens.subjectOfRefresh("refresh")).thenReturn(user.getId());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(tokens.issueAccess(user)).thenReturn("access-2");
        when(tokens.issueRefresh(user)).thenReturn("refresh-2");
        when(tokens.accessTtl()).thenReturn(Duration.ofMinutes(15));

        assertThat(authService().refresh(new RefreshRequest("refresh")).accessToken())
                .isEqualTo("access-2");
    }

    @Test
    void refreshOfDeletedUserIsRejected() {
        UUID id = UUID.randomUUID();
        when(tokens.subjectOfRefresh("refresh")).thenReturn(id);
        when(users.findById(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() -> authService().refresh(new RefreshRequest("refresh")));
    }

    private User user() {
        return stored(new User("ivan@payflow.ru", passwords.encode(PASSWORD), Role.USER));
    }

    private static User stored(User user) {
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
