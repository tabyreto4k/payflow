package ru.payflow.order.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import ru.payflow.order.auth.model.User;
import ru.payflow.order.config.JwtProperties;
import ru.payflow.order.exception.InvalidTokenException;

/**
 * Access-токен разбирает gateway, refresh — только этот сервис. Тип зашит в claim {@code type}:
 * без него refresh-токен ходил бы вместо access и жил бы неделю вместо пятнадцати минут.
 */
@Service
public class TokenService {

    static final String TYPE_CLAIM = "type";
    static final String ROLES_CLAIM = "roles";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder encoder, JwtDecoder decoder, JwtProperties properties) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.properties = properties;
    }

    public String issueAccess(User user) {
        return issue(user, ACCESS, properties.accessTtl());
    }

    public String issueRefresh(User user) {
        return issue(user, REFRESH, properties.refreshTtl());
    }

    public Duration accessTtl() {
        return properties.accessTtl();
    }

    /** Проверяет подпись, срок и тип токена; возвращает id пользователя из {@code sub}. */
    public UUID subjectOfRefresh(String token) {
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException e) {
            throw new InvalidTokenException("Refresh-токен недействителен");
        }
        if (!REFRESH.equals(jwt.getClaimAsString(TYPE_CLAIM))) {
            throw new InvalidTokenException("Ожидался refresh-токен");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("Refresh-токен недействителен");
        }
    }

    private String issue(User user, String type, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(TYPE_CLAIM, type)
                .claim(ROLES_CLAIM, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
