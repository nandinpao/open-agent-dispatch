package com.opensocket.aievent.core.runtime.capability;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceAccessEnforcementMode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public final class RuntimeCapabilityService {
    private final Environment environment;
    private final Clock clock;

    public RuntimeCapabilityService(Environment environment, Clock clock) {
        this.environment = environment;
        this.clock = clock;
    }

    public RuntimeCapabilitySnapshot snapshot() {
        boolean iamApi = enabled("aeg.iam.api.enabled");
        boolean iamRuntime = enabled("aeg.iam.runtime.enabled");
        boolean iamAuthentication = enabled("aeg.iam.authentication.enabled");
        boolean iamRbac = enabled("aeg.iam.rbac.enabled");
        boolean iamToken = enabled("aeg.iam.token.enabled");
        boolean iamFullyActive = iamApi && iamRuntime && iamAuthentication && iamRbac && iamToken;

        return new RuntimeCapabilitySnapshot(
                "4.0",
                Instant.now(clock),
                new RuntimeCapabilitySnapshot.AuthenticationCapability(
                        "/api/session",
                        enabled("aeg.iam.credentials.legacy-password-adapter-enabled")),
                new RuntimeCapabilitySnapshot.RuntimeSurfaces(
                        iamFullyActive ? RuntimeCapabilityState.ENABLED : RuntimeCapabilityState.DISABLED,
                        resourceAccessState(),
                        uiCapabilityState(),
                        enforcementActivationState(),
                        RuntimeCapabilityState.ENABLED,
                        issueTrackingState()));
    }

    private RuntimeCapabilityState resourceAccessState() {
        if (!enabled("resource-access.enabled")) return RuntimeCapabilityState.DISABLED;
        boolean administration = enabled("resource-access.management-api-enabled")
                || enabled("resource-access.governance-api-enabled");
        if (!administration) return RuntimeCapabilityState.DISABLED;
        ResourceAccessEnforcementMode mode = enforcementMode("resource-access.enforcement-mode", ResourceAccessEnforcementMode.OFF);
        if (mode == ResourceAccessEnforcementMode.OFF || mode == ResourceAccessEnforcementMode.SHADOW) {
            return RuntimeCapabilityState.SHADOW;
        }
        if (mode == ResourceAccessEnforcementMode.READ_ENFORCE || mode == ResourceAccessEnforcementMode.WRITE_ENFORCE) {
            return RuntimeCapabilityState.PILOT;
        }
        return RuntimeCapabilityState.ENABLED;
    }

    private RuntimeCapabilityState uiCapabilityState() {
        if (!enabled("ui-capability.enabled") || !enabled("ui-capability.projection-api-enabled")) {
            return RuntimeCapabilityState.DISABLED;
        }
        return enabled("ui-capability.page-bootstrap-enabled")
                ? RuntimeCapabilityState.ENABLED
                : RuntimeCapabilityState.PILOT;
    }

    private RuntimeCapabilityState enforcementActivationState() {
        if (!enabled("aeg.enforcement-activation.control-plane-enabled")) {
            return RuntimeCapabilityState.DISABLED;
        }
        if (enabled("aeg.enforcement-activation.wave0-read-pilot-enabled")
                || enabled("aeg.enforcement-activation.task-list-search-pilot-enabled")) {
            return RuntimeCapabilityState.PILOT;
        }
        boolean resourceDecisionAuthority = enabled("resource-access.enabled")
                && enabled("resource-access.decision-api-enabled");
        boolean revisionSynchronization = enabled("aeg.enforcement-activation.revision-sync-enabled");
        if (!resourceDecisionAuthority || !revisionSynchronization) {
            return RuntimeCapabilityState.SHADOW;
        }
        return RuntimeCapabilityState.ENABLED;
    }

    private RuntimeCapabilityState issueTrackingState() {
        boolean tracking = enabled("issue-tracking.enabled");
        boolean projection = enabled("issue-projection.enabled");
        if (tracking && projection) return RuntimeCapabilityState.ENABLED;
        if (tracking || projection) return RuntimeCapabilityState.PILOT;
        return RuntimeCapabilityState.DISABLED;
    }

    private boolean enabled(String property) {
        return environment.getProperty(property, Boolean.class, false);
    }

    private ResourceAccessEnforcementMode enforcementMode(String property, ResourceAccessEnforcementMode fallback) {
        return environment.getProperty(property, ResourceAccessEnforcementMode.class, fallback);
    }
}
