package ru.payflow.order.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import ru.payflow.order.security.CustomerHeaderAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String HMAC = "HmacSHA256";

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
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                // Отказ уходит через тот же @RestControllerAdvice, что и остальные ошибки, —
                // иначе на 401 клиент получал бы пустое тело вместо ProblemDetail.
                .exceptionHandling(handling -> handling.authenticationEntryPoint(
                                (request, response, e) -> resolver.resolveException(request, response, null, e))
                        .accessDeniedHandler(
                                (request, response, e) -> resolver.resolveException(request, response, null, e)))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        return NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static SecretKeySpec secretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), HMAC);
    }
}
