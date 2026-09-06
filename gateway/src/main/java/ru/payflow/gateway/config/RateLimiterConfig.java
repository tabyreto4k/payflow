package ru.payflow.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import ru.payflow.gateway.security.JwtAuthFilter;

@Configuration
public class RateLimiterConfig {

    private static final String ANONYMOUS = "anonymous";

    // Перед gateway стоит ровно один прокси — nginx. Иначе адресом всех анонимных клиентов был бы
    // адрес nginx, и одно ведро на всех.
    private static final RemoteAddressResolver BEHIND_NGINX = XForwardedRemoteAddressResolver.maxTrustedIndex(1);

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
            var remote = BEHIND_NGINX.resolve(exchange);
            return Mono.just(remote == null ? ANONYMOUS : remote.getAddress().getHostAddress());
        };
    }
}
