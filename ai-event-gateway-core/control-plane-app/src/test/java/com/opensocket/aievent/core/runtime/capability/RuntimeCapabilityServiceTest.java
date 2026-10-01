package com.opensocket.aievent.core.runtime.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import tools.jackson.databind.json.JsonMapper;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.integration.issue.projection.IssueProjectionProperties;
import com.opensocket.aievent.core.integration.issue.projection.IssueProjectionRuntimeConfigurationView;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RuntimeCapabilityServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-02T05:00:00Z"), ZoneOffset.UTC);

    @Test
    void dormantBundleStillPublishesCanonicalSessionContract() {
        RuntimeCapabilitySnapshot snapshot = service(new MockEnvironment()).snapshot();

        assertThat(snapshot.contractVersion()).isEqualTo("4.0");
        assertThat(snapshot.authentication().sessionPath()).isEqualTo("/api/session");
        assertThat(snapshot.authentication().legacyPasswordAdapterEnabled()).isFalse();
        assertThat(snapshot.surfaces().iamAdministration()).isEqualTo(RuntimeCapabilityState.DISABLED);
    }

    @Test
    void activeRuntimePublishesOneCanonicalAdministrationSurface() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("aeg.iam.api.enabled", "true")
                .withProperty("aeg.iam.runtime.enabled", "true")
                .withProperty("aeg.iam.authentication.enabled", "true")
                .withProperty("aeg.iam.rbac.enabled", "true")
                .withProperty("aeg.iam.token.enabled", "true")
                .withProperty("aeg.iam.credentials.legacy-password-adapter-enabled", "true")
                .withProperty("resource-access.enabled", "true")
                .withProperty("resource-access.management-api-enabled", "true")
                .withProperty("resource-access.enforcement-mode", "SHADOW")
                .withProperty("ui-capability.enabled", "true")
                .withProperty("ui-capability.projection-api-enabled", "true")
                .withProperty("ui-capability.page-bootstrap-enabled", "true");

        RuntimeCapabilitySnapshot snapshot = service(environment).snapshot();

        assertThat(snapshot.authentication().sessionPath()).isEqualTo("/api/session");
        assertThat(snapshot.authentication().legacyPasswordAdapterEnabled()).isTrue();
        assertThat(snapshot.surfaces().iamAdministration()).isEqualTo(RuntimeCapabilityState.ENABLED);
        assertThat(snapshot.surfaces().resourceAccessAdministration()).isEqualTo(RuntimeCapabilityState.SHADOW);
        assertThat(snapshot.surfaces().uiCapabilityProjection()).isEqualTo(RuntimeCapabilityState.ENABLED);
    }

    @Test
    void administrativeEnforcementControlPlaneIsReportedAsShadowWithoutDecisionAuthority() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("aeg.enforcement-activation.control-plane-enabled", "true")
                .withProperty("aeg.enforcement-activation.revision-sync-enabled", "false")
                .withProperty("resource-access.enabled", "false")
                .withProperty("resource-access.decision-api-enabled", "false");

        RuntimeCapabilitySnapshot snapshot = service(environment).snapshot();

        assertThat(snapshot.surfaces().enforcementActivation()).isEqualTo(RuntimeCapabilityState.SHADOW);
    }

    @Test
    void synchronizedEnforcementControlPlaneIsEnabledWithDecisionAuthority() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("aeg.enforcement-activation.control-plane-enabled", "true")
                .withProperty("aeg.enforcement-activation.revision-sync-enabled", "true")
                .withProperty("resource-access.enabled", "true")
                .withProperty("resource-access.decision-api-enabled", "true");

        RuntimeCapabilitySnapshot snapshot = service(environment).snapshot();

        assertThat(snapshot.surfaces().enforcementActivation()).isEqualTo(RuntimeCapabilityState.ENABLED);
    }

    private static RuntimeCapabilityService service(MockEnvironment environment) {
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        IssueProjectionProperties startup = new IssueProjectionProperties();
        if (environment.containsProperty("issue-projection.enabled")) {
            startup.setEnabled(environment.getProperty("issue-projection.enabled", Boolean.class, true));
        }
        IssueProjectionRuntimeConfigurationView projection = new IssueProjectionRuntimeConfigurationView(startup, values, authority);
        return new RuntimeCapabilityService(environment, CLOCK, projection);
    }
}
