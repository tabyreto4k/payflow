package ru.payflow.payment.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import ru.payflow.payment.security.CustomerHeaderAuthenticationFilter;

/**
 * Сервис токенов не разбирает: их проверил gateway, сюда приезжает уже готовая личность в
 * заголовках. Снаружи периметра порт закрыт — на этом и держится доверие.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)
            throws Exception {
        // CSRF выключён осознанно, и это не упущение: сессий и cookie здесь нет (STATELESS),
        // личность приезжает заголовком от gateway. Атака подделкой запроса эксплуатирует
        // браузерный контекст с ambient-кредами — его у этого API не существует.
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new CustomerHeaderAuthenticationFilter(), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/actuator/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(
                                (request, response, e) -> resolver.resolveException(request, response, null, e))
                        .accessDeniedHandler(
                                (request, response, e) -> resolver.resolveException(request, response, null, e)))
                .build();
    }
}
