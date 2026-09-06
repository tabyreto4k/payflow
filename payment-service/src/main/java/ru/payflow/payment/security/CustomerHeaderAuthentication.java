package ru.payflow.payment.security;

import java.util.Collection;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** Личность, пришедшая из заголовков gateway. Учётных данных здесь нет — их проверил gateway. */
public class CustomerHeaderAuthentication extends AbstractAuthenticationToken {

    private final UUID customerId;

    public CustomerHeaderAuthentication(UUID customerId, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.customerId = customerId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public UUID getPrincipal() {
        return customerId;
    }
}
