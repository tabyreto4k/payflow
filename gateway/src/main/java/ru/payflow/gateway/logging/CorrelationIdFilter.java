package ru.payflow.gateway.logging;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Сквозной идентификатор запроса заводится на периметре: дальше он живёт в MDC сервисов и в
 * заголовках событий Kafka, и по нему один поход клиента собирается из логов всех сервисов сразу.
 *
 * <p>Если nginx уже проставил {@code X-Request-Id} — берём его: два разных идентификатора на один
 * запрос сшивать потом больнее, чем завести один.
 *
 * <p>MDC здесь не выставляется намеренно. Стек реактивный, обработка одного запроса размазана по
 * потокам event-loop'а, и ThreadLocal показывал бы чужой идентификатор. Логи gateway обходятся
 * своими средствами, а сквозной идентификатор нужен именно сервисам за ним.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = firstPresent(
                exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER),
                exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER));

        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        return chain.filter(exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(CORRELATION_ID_HEADER, correlationId)))
                .build());
    }

    @Override
    public int getOrder() {
        // Прежде JwtAuthFilter: отказ по токену тоже должен уходить с идентификатором.
        return Ordered.HIGHEST_PRECEDENCE - 1;
    }

    private static String firstPresent(String correlationId, String requestId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }
}
