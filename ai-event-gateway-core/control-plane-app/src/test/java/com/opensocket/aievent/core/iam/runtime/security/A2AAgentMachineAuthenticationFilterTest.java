package com.opensocket.aievent.core.iam.runtime.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class A2AAgentMachineAuthenticationFilterTest {
    @Test
    void recognizesOnlyNonPatBearerOnCanonicalA2ARequestRoute() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks/task-1/a2a-requests");
        request.addHeader("Authorization", "Bearer agent-secret");
        assertThat(A2AAgentMachineAuthenticationFilter.hasAgentA2ABearer(request)).isTrue();

        MockHttpServletRequest pat = new MockHttpServletRequest("POST", "/api/tasks/task-1/a2a-requests");
        pat.addHeader("Authorization", "Bearer odp_pat_secret");
        assertThat(A2AAgentMachineAuthenticationFilter.hasAgentA2ABearer(pat)).isFalse();

        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/tasks/task-1/cancel");
        other.addHeader("Authorization", "Bearer agent-secret");
        assertThat(A2AAgentMachineAuthenticationFilter.hasAgentA2ABearer(other)).isFalse();
    }
}
