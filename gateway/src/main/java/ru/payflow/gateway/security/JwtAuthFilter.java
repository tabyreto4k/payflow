package ru.payflow.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * Единственная точка, где проверяется подпись токена: сервисы за периметром доверяют заголовкам.
 * Поэтому клиентские {@code X-Customer-Id}/{@code X-Roles} срезаются всегда — и на открытых путях
 * тоже, иначе подделать личность можно было бы запросом к {@code /api/v1/auth/**}.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    public static final String CUSTOMER_ID_HEADER = "X-Customer-Id";
    public static final String ROLES_HEADER = "X-Roles";

    private static final String BEARER = "Bearer ";
    private static final String TYPE_CLAIM = "type";
    private static final String ROLES_CLAIM = "roles";
    private static final String ACCESS = "access";

    private static final List<PathPattern> PUBLIC_PATHS = List.of(
            pattern("/api/v1/auth/**"), pattern("/actuator/**"), pattern("/swagger-ui/**"), pattern("/v3/api-docs/**"));

    private final JwtDecoder decoder;
    private final ObjectMapper json;

    public JwtAuthFilter(JwtDecoder decoder, ObjectMapper json) {
        this.decoder = decoder;
        this.json = json;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest stripped = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.remove(CUSTOMER_ID_HEADER);
                    headers.remove(ROLES_HEADER);
                })
                .build();

        if (isPublic(exchange)) {
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        String header = stripped.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return unauthorized(exchange, "Нужен заголовок Authorization: Bearer <access-токен>");
        }

        Jwt token;
        try {
            token = decoder.decode(header.substring(BEARER.length()).trim());
        } catch (JwtException e) {
            return unauthorized(exchange, "Токен недействителен или просрочен");
        }
        if (!ACCESS.equals(token.getClaimAsString(TYPE_CLAIM))) {
            return unauthorized(exchange, "Ожидался access-токен");
        }

        ServerHttpRequest identified = stripped.mutate()
                .header(CUSTOMER_ID_HEADER, token.getSubject())
                .header(ROLES_HEADER, token.getClaimAsString(ROLES_CLAIM))
                .build();
        return chain.filter(exchange.mutate().request(identified).build());
    }

    @Override
    public int getOrder() {
        // Раньше всех: rate limiter считает запросы по X-Customer-Id, который проставляется здесь.
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isPublic(ServerWebExchange exchange) {
        var path = exchange.getRequest().getPath().pathWithinApplication();
        return PUBLIC_PATHS.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problem.setTitle("Не аутентифицирован");

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body;
        try {
            body = json.writeValueAsBytes(problem);
        } catch (JsonProcessingException e) {
            body = new byte[0];
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private static PathPattern pattern(String path) {
        return PathPatternParser.defaultInstance.parse(path);
    }
}
