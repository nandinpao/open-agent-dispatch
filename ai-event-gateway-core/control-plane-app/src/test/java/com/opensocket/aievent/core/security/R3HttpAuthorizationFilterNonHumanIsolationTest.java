package com.opensocket.aievent.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class R3HttpAuthorizationFilterNonHumanIsolationTest {
    private final R3HttpAuthorizationFilter filter = new R3HttpAuthorizationFilter(null, null, null);

    @Test
    void eventIntakeMustNeverEnterHumanR3Authorization() {
        assertSkipped("POST", "/api/events/intake");
    }

    @Test
    void machineOauthAndJwksMustNeverEnterHumanR3Authorization() {
        assertSkipped("POST", "/oauth2/token");
        assertSkipped("GET", "/oauth2/jwks");
        assertSkipped("POST", "/oauth/token");
        assertSkipped("GET", "/.well-known/jwks.json");
    }

    @Test
    void internalAndActuatorRoutesMustNeverEnterHumanR3Authorization() {
        assertSkipped("POST", "/internal/control-plane/tasks/task-1/ack");
        assertSkipped("GET", "/actuator/health");
    }

    @Test
    void providerWebhooksMustNeverEnterHumanR3Authorization() {
        assertSkipped("POST", "/api/external/provider-webhooks/redmine");
    }

    private void assertSkipped(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }
}
