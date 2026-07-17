package com.opensocket.aievent.gateway.netty.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.opensocket.aievent.gateway.netty.config.AdminProperties;

class MachineAdminTokenAuthFilterTest {
    @Test
    void protectsAdminApiWithMachineCredential() throws Exception {
        AdminProperties properties = hardened();
        MachineAdminTokenAuthFilter filter = new MachineAdminTokenAuthFilter(properties);
        MockHttpServletRequest denied = new MockHttpServletRequest("GET", "/api/admin/events");
        MockHttpServletResponse deniedResponse = new MockHttpServletResponse();
        filter.doFilter(denied, deniedResponse, new MockFilterChain());
        assertThat(deniedResponse.getStatus()).isEqualTo(401);

        MockHttpServletRequest allowed = new MockHttpServletRequest("GET", "/api/admin/events");
        allowed.addHeader("Authorization", "Bearer netty-machine-secret");
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();
        filter.doFilter(allowed, allowedResponse, (request, response) -> reached.set(true));
        assertThat(reached).isTrue();
        assertThat(allowed.getAttribute(MachineAdminTokenAuthFilter.MACHINE_AUTHENTICATED_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
    }

    @Test
    void separatesInternalClusterCredentialFromAdminMachineCredential() throws Exception {
        MachineAdminTokenAuthFilter filter = new MachineAdminTokenAuthFilter(hardened());
        MockHttpServletRequest wrong = new MockHttpServletRequest("POST", "/internal/directory/sync");
        wrong.addHeader("Authorization", "Bearer netty-machine-secret");
        MockHttpServletResponse wrongResponse = new MockHttpServletResponse();
        filter.doFilter(wrong, wrongResponse, new MockFilterChain());
        assertThat(wrongResponse.getStatus()).isEqualTo(401);

        MockHttpServletRequest allowed = new MockHttpServletRequest("POST", "/internal/directory/sync");
        allowed.addHeader("X-Cluster-Token", "cluster-internal-secret");
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();
        filter.doFilter(allowed, allowedResponse, (request, response) -> reached.set(true));
        assertThat(reached).isTrue();
    }

    @Test
    void neverAcceptsBrowserCookieOrQueryToken() throws Exception {
        MachineAdminTokenAuthFilter filter = new MachineAdminTokenAuthFilter(hardened());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/events");
        request.setQueryString("token=netty-machine-secret");
        request.addHeader("Cookie", "OPENDISPATCH_ADMIN_WS_TOKEN=netty-machine-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    private AdminProperties hardened() {
        AdminProperties properties = new AdminProperties();
        properties.setMachineAuthEnabled(true);
        properties.setMachineToken("netty-machine-secret");
        properties.setInternalToken("cluster-internal-secret");
        properties.setMachineWebSocketHandshakeAuthEnabled(true);
        return properties;
    }
}
