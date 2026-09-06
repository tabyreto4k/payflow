package ru.payflow.order.auth.service;

import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import ru.payflow.order.exception.NotFoundException;

@Service
public class AuthService {

    private static final String BEARER = "Bearer";

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;

    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }
        User user = users.save(new User(email, passwords.encode(request.password()), Role.USER));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email())).orElse(null);
        if (user == null) {
            // Хеш всё равно считаем: без этого быстрый отказ выдаёт, какие адреса не заняты.
            passwords.encode(request.password());
            throw new InvalidCredentialsException();
        }
        if (!passwords.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issue(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest request) {
        User user = users.findById(tokens.subjectOfRefresh(request.refreshToken()))
                .orElseThrow(() -> new InvalidTokenException("Пользователь из токена больше не существует"));
        return issue(user);
    }

    /**
     * Адрес покупателя для писем. Заказ уносит его в событие: кроме order-service email не знает
     * никто, а лезть в чужую БД notification-service не может.
     */
    @Transactional(readOnly = true)
    public String emailOf(UUID customerId) {
        return users.findById(customerId)
                .map(User::getEmail)
                .orElseThrow(() -> new NotFoundException("Покупатель не найден: " + customerId));
    }

    private TokenResponse issue(User user) {
        return new TokenResponse(
                tokens.issueAccess(user),
                tokens.issueRefresh(user),
                BEARER,
                tokens.accessTtl().toSeconds());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
