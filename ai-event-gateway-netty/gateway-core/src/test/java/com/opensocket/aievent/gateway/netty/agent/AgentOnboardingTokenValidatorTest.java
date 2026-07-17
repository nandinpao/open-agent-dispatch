package com.opensocket.aievent.gateway.netty.agent;

import com.opensocket.aievent.gateway.netty.config.AgentProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOnboardingTokenValidatorTest {

    @Test
    void shouldAcceptPrimaryOnboardingToken() {
        var properties = securedProperties();
        var validator = new AgentOnboardingTokenValidator(properties);

        assertThat(validator.isAuthorizedToken("primary-token")).isTrue();
    }

    @Test
    void shouldAcceptAdditionalOnboardingTokenDuringRotationWindow() {
        var properties = securedProperties();
        properties.setAdditionalOnboardingTokens(List.of("previous-token", "next-token"));
        var validator = new AgentOnboardingTokenValidator(properties);

        assertThat(validator.isAuthorizedToken("previous-token")).isTrue();
        assertThat(validator.isAuthorizedToken("next-token")).isTrue();
    }

    @Test
    void shouldRejectUnknownTokenWhenAuthEnabled() {
        var properties = securedProperties();
        properties.setAdditionalOnboardingTokens(List.of("previous-token"));
        var validator = new AgentOnboardingTokenValidator(properties);

        assertThat(validator.isAuthorizedToken("wrong-token")).isFalse();
    }

    @Test
    void shouldTreatAdditionalTokenOnlyAsConfigured() {
        var properties = new AgentProperties();
        properties.setAuthEnabled(true);
        properties.setAdditionalOnboardingTokens(List.of("rotation-only-token"));
        var validator = new AgentOnboardingTokenValidator(properties);

        assertThat(validator.configured()).isTrue();
        assertThat(validator.isAuthorizedToken("rotation-only-token")).isTrue();
    }

    private AgentProperties securedProperties() {
        var properties = new AgentProperties();
        properties.setAuthEnabled(true);
        properties.setOnboardingToken("primary-token");
        return properties;
    }
}
