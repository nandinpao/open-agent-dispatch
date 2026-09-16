package com.opensocket.aievent.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.pattern.PathPatternParser;

class CoreInternalSecurityConfigurationTest {
    @Test
    void adapterActionRecoveryMatcherIsCompatibleWithSpringPathPatternParser() {
        var parser = new PathPatternParser();
        var pattern = parser.parse(
                CoreInternalSecurityConfiguration.ADAPTER_ACTION_RECOVER_EXPIRED_LEASE_PATTERN);

        assertThat(pattern.matches(
                org.springframework.http.server.PathContainer.parsePath(
                        "/internal/adapter-actions/action-001/recover-expired-lease")))
                .isTrue();
        assertThat(pattern.matches(
                org.springframework.http.server.PathContainer.parsePath(
                        "/internal/adapter-actions/action-001/nested/recover-expired-lease")))
                .isFalse();
    }

    @Test
    void gatewayAgentInternalEndpointsAreExplicitGatewayPaths() {
        assertThat(CoreInternalSecurityConfiguration.AGENT_AUTHORIZE_CONNECTION_PATTERN)
                .isEqualTo("/internal/agents/authorize-connection");
        assertThat(CoreInternalSecurityConfiguration.AGENT_SECURITY_EVENTS_PATTERN)
                .isEqualTo("/internal/agents/security-events");
        assertThat(CoreInternalSecurityConfiguration.AGENT_ENROLLMENTS_PATTERN)
                .isEqualTo("/internal/agents/enrollments");
    }

    @Test
    void internalMachineAuthorityUsesExplicitSpringAuthorityCode() {
        assertThat(CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.OPERATOR))
                .isEqualTo("ROLE_OPERATOR");
    }
}
