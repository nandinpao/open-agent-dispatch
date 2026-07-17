package com.opensocket.aievent.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CoreInternalTokenVerifierTest {
    @Test
    void verifiesAdminCompatibilityTokenUsingConstantRoleClassification() {
        CoreInternalSecurityProperties properties = enabledProperties();
        CoreInternalTokenVerifier verifier = new CoreInternalTokenVerifier(
                properties, new CoreInternalSecurityRequestClassifier(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/agents/agent-1/disable");
        request.addHeader("X-Cluster-Token", "operator-secret");

        CoreInternalTokenVerifier.Verification result = verifier.verify(request);
        assertThat(result.required()).isTrue();
        assertThat(result.accepted()).isTrue();
        assertThat(result.role()).isEqualTo(CoreInternalSecurityRole.OPERATOR);
    }

    @Test
    void leavesCoreHumanLoginOutsideMachineTokenClassification() {
        CoreInternalSecurityProperties properties = enabledProperties();
        CoreInternalTokenVerifier verifier = new CoreInternalTokenVerifier(
                properties, new CoreInternalSecurityRequestClassifier(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

        CoreInternalTokenVerifier.Verification result = verifier.verify(request);
        assertThat(result.required()).isFalse();
        assertThat(result.accepted()).isFalse();
    }

    @Test
    void usesDedicatedEventIntakeCredential() {
        CoreInternalSecurityProperties properties = enabledProperties();
        properties.setEventIntakeToken("event-secret");
        CoreInternalTokenVerifier verifier = new CoreInternalTokenVerifier(
                properties, new CoreInternalSecurityRequestClassifier(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events/intake");
        request.addHeader("X-Cluster-Token", "event-secret");

        assertThat(verifier.verify(request).accepted()).isTrue();
    }

    @Test
    void rejectsInvalidAdminCompatibilityToken() {
        CoreInternalSecurityProperties properties = enabledProperties();
        CoreInternalTokenVerifier verifier = new CoreInternalTokenVerifier(
                properties, new CoreInternalSecurityRequestClassifier(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/agents");
        request.addHeader("X-Cluster-Token", "wrong");

        CoreInternalTokenVerifier.Verification result = verifier.verify(request);
        assertThat(result.required()).isTrue();
        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo("invalid_request_token");
    }

    private CoreInternalSecurityProperties enabledProperties() {
        CoreInternalSecurityProperties properties = new CoreInternalSecurityProperties();
        properties.setEnabled(true);
        properties.setOperatorToken("operator-secret");
        return properties;
    }
}
