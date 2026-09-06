package ru.payflow.order.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CustomerHeaderAuthenticationFilterTest {

    private final CustomerHeaderAuthenticationFilter filter = new CustomerHeaderAuthenticationFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerIdFromGatewayBecomesPrincipal() throws Exception {
        UUID customerId = UUID.randomUUID();

        Authentication authentication = authenticate(customerId.toString(), "USER");

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(customerId);
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void severalRolesAreSplit() throws Exception {
        assertThat(authenticate(UUID.randomUUID().toString(), "user, admin").getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void requestWithoutHeaderStaysAnonymous() throws Exception {
        assertThat(authenticate(null, null)).isNull();
    }

    @Test
    void malformedCustomerIdStaysAnonymous() throws Exception {
        assertThat(authenticate("not-a-uuid", "USER")).isNull();
    }

    private Authentication authenticate(String customerId, String roles) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (customerId != null) {
            request.addHeader(CustomerHeaderAuthenticationFilter.CUSTOMER_ID_HEADER, customerId);
        }
        if (roles != null) {
            request.addHeader(CustomerHeaderAuthenticationFilter.ROLES_HEADER, roles);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        // Фильтр только проставляет личность: запрос идёт дальше и без заголовков.
        Mockito.verify(chain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
