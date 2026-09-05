package ru.payflow.gateway.config;

import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import ru.payflow.gateway.security.JwtAuthFilter;

@Configuration
public class RateLimiterConfig {

    private static final String ANONYMOUS = "anonymous";

    /**
     * Ключ ведра — покупатель, для анонимных запросов (логин, регистрация) — адрес клиента.
     * Заголовок к этому моменту проставлен {@link JwtAuthFilter}: свой клиент подсунуть не может.
     */
    @Bean
    KeyResolver customerKeyResolver() {
        return exchange -> {
            String customerId = exchange.getRequest().getHeaders().getFirst(JwtAuthFilter.CUSTOMER_ID_HEADER);
            if (customerId != null && !customerId.isBlank()) {
                return Mono.just(customerId);
            }
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            return Mono.just(remote == null ? ANONYMOUS : remote.getAddress().getHostAddress());
        };
    }
}
