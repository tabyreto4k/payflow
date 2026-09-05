package ru.payflow.order.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Сервис живёт за gateway и доверяет его заголовкам: токен уже проверен, свои заголовки с этими
 * именами gateway срезает. Снаружи периметра порт сервиса закрыт — доверие держится на этом.
 */
public class CustomerHeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String CUSTOMER_ID_HEADER = "X-Customer-Id";
    public static final String ROLES_HEADER = "X-Roles";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        UUID customerId = customerId(request.getHeader(CUSTOMER_ID_HEADER));
        if (customerId != null) {
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new CustomerHeaderAuthentication(customerId, authorities(request.getHeader(ROLES_HEADER))));
        }
        chain.doFilter(request, response);
    }

    private static UUID customerId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<GrantedAuthority> authorities(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
                .toList();
    }
}
