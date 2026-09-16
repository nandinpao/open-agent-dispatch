package com.opensocket.aievent.core.runtime.capability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RuntimeCapabilityCompatibilityValidatorTest {
    @Test
    void dormantCanonicalBundleIsValid() {
        assertThat(RuntimeCapabilityCompatibilityValidator.validate(new MockEnvironment())).isEmpty();
    }

    @Test
    void partiallyEnabledIamBundleIsRejected() {
        MockEnvironment environment = new MockEnvironment().withProperty("aeg.iam.api.enabled", "true");
        assertThat(RuntimeCapabilityCompatibilityValidator.validate(environment))
                .contains("Canonical IAM requires API, runtime, authentication, RBAC and token flags together");
    }

    @Test
    void activeAdapterRequiresCredentialRepository() {
        MockEnvironment environment = fullyEnabledIam()
                .withProperty("aeg.iam.credentials.legacy-password-adapter-enabled", "true")
                .withProperty("core.security.admin.enabled", "false");
        assertThat(RuntimeCapabilityCompatibilityValidator.validate(environment))
                .contains("Legacy password adapter requires the bounded legacy credential repository");
    }

    @Test
    void fullyEnabledCanonicalBundleWithCredentialAdapterIsValid() {
        MockEnvironment environment = fullyEnabledIam()
                .withProperty("core.security.admin.enabled", "true")
                .withProperty("aeg.iam.credentials.legacy-password-adapter-enabled", "true");
        assertThat(RuntimeCapabilityCompatibilityValidator.validate(environment)).isEmpty();
    }

    @Test
    void administrativeControlPlaneDoesNotRequireResourceDecisionAuthority() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("aeg.enforcement-activation.control-plane-enabled", "true")
                .withProperty("aeg.enforcement-activation.revision-sync-enabled", "false")
                .withProperty("aeg.enforcement-activation.wave0-read-pilot-enabled", "false")
                .withProperty("aeg.enforcement-activation.task-list-search-pilot-enabled", "false")
                .withProperty("resource-access.enabled", "false")
                .withProperty("resource-access.decision-api-enabled", "false");

        assertThat(RuntimeCapabilityCompatibilityValidator.validate(environment)).isEmpty();
    }

    @Test
    void revisionSynchronizationRequiresResourceDecisionAuthority() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("aeg.enforcement-activation.control-plane-enabled", "true")
                .withProperty("aeg.enforcement-activation.revision-sync-enabled", "true")
                .withProperty("resource-access.enabled", "false")
                .withProperty("resource-access.decision-api-enabled", "false");

        assertThat(RuntimeCapabilityCompatibilityValidator.validate(environment))
                .contains("Enforcement Activation runtime features require Resource Access decision authority");
    }

    @Test
    void revisionSynchronizationWithResourceDecisionAuthorityIsValid() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("aeg.enforcement-activation.control-plane-enabled", "true")
                .withProperty("aeg.enforcement-activation.revision-sync-enabled", "true")
                .withProperty("resource-access.enabled", "true")
                .withProperty("resource-access.decision-api-enabled", "true");

        assertThat(RuntimeCapabilityCompatibilityValidator.validate(environment)).isEmpty();
    }

    private static MockEnvironment fullyEnabledIam() {
        return new MockEnvironment()
                .withProperty("aeg.iam.api.enabled", "true")
                .withProperty("aeg.iam.runtime.enabled", "true")
                .withProperty("aeg.iam.authentication.enabled", "true")
                .withProperty("aeg.iam.rbac.enabled", "true")
                .withProperty("aeg.iam.token.enabled", "true");
    }
}
