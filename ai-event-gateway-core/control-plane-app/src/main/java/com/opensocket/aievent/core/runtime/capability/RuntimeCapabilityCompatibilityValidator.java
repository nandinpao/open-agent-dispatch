package com.opensocket.aievent.core.runtime.capability;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceAccessEnforcementMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fails startup when independently configured feature flags describe an impossible runtime. */
@Component
public final class RuntimeCapabilityCompatibilityValidator implements SmartInitializingSingleton {
    private final Environment environment;
    private final boolean failFast;

    public RuntimeCapabilityCompatibilityValidator(
            Environment environment,
            @Value("${runtime-capabilities.fail-fast:true}") boolean failFast) {
        this.environment = environment;
        this.failFast = failFast;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!failFast) return;
        List<String> blockers = validate(environment);
        if (!blockers.isEmpty()) {
            throw new IllegalStateException(
                    "RUNTIME_CAPABILITY_CONFIGURATION_INVALID: " + String.join("; ", blockers));
        }
    }

    static List<String> validate(Environment environment) {
        List<String> blockers = new ArrayList<>();
        boolean iamApi = bool(environment, "aeg.iam.api.enabled");
        boolean iamRuntime = bool(environment, "aeg.iam.runtime.enabled");
        boolean iamAuthentication = bool(environment, "aeg.iam.authentication.enabled");
        boolean iamRbac = bool(environment, "aeg.iam.rbac.enabled");
        boolean iamToken = bool(environment, "aeg.iam.token.enabled");
        boolean anyIamFoundation = iamApi || iamRuntime || iamAuthentication || iamRbac || iamToken;
        if (anyIamFoundation && !(iamApi && iamRuntime && iamAuthentication && iamRbac && iamToken)) {
            blockers.add("Canonical IAM requires API, runtime, authentication, RBAC and token flags together");
        }

        boolean legacyCredentialAdapter = bool(environment, "aeg.iam.credentials.legacy-password-adapter-enabled");
        boolean legacyCredentialRepository = bool(environment, "core.security.admin.enabled");
        if (legacyCredentialAdapter && !legacyCredentialRepository) {
            blockers.add("Legacy password adapter requires the bounded legacy credential repository");
        }

        boolean resourceAccess = bool(environment, "resource-access.enabled");
        List<String> resourceSubFlags = List.of(
                "resource-access.management-api-enabled",
                "resource-access.governance-api-enabled",
                "resource-access.decision-api-enabled",
                "resource-access.task-enabled",
                "resource-access.a2a-enabled",
                "resource-access.issue-enabled",
                "resource-access.integration-enabled",
                "resource-access.external-write-enabled",
                "resource-access.attachment-enabled",
                "resource-access.export-enabled",
                "resource-access.background-enabled",
                "resource-access.runtime-lease-enabled",
                "resource-access.scope-snapshot-enabled",
                "resource-access.runtime-scale-api-enabled",
                "resource-access.agent-admin-enabled",
                "resource-access.field-visibility-enabled",
                "resource-access.explicit-deny-enabled",
                "resource-access.break-glass-enabled",
                "resource-access.shadow-pipeline.enabled");
        if (!resourceAccess && resourceSubFlags.stream().anyMatch(property -> bool(environment, property))) {
            blockers.add("Resource Access sub-features cannot be enabled while resource-access.enabled=false");
        }
        ResourceAccessEnforcementMode enforcement = environment.getProperty(
                "resource-access.enforcement-mode",
                ResourceAccessEnforcementMode.class,
                ResourceAccessEnforcementMode.OFF);
        if (enforcement != ResourceAccessEnforcementMode.OFF
                && (!resourceAccess || !bool(environment, "resource-access.decision-api-enabled"))) {
            blockers.add("Resource Access enforcement requires the base feature and decision API");
        }

        boolean uiCapability = bool(environment, "ui-capability.enabled");
        boolean uiProjection = bool(environment, "ui-capability.projection-api-enabled");
        boolean uiBootstrap = bool(environment, "ui-capability.page-bootstrap-enabled");
        boolean accessRequest = bool(environment, "ui-capability.access-request-enabled");
        if (!uiCapability && (uiProjection || uiBootstrap || accessRequest)) {
            blockers.add("UI Capability sub-features require ui-capability.enabled=true");
        }
        if (uiBootstrap && !uiProjection) {
            blockers.add("UI Capability page bootstrap requires the projection API");
        }
        if (accessRequest && (!resourceAccess || !bool(environment, "resource-access.governance-api-enabled"))) {
            blockers.add("UI access requests require Resource Access governance");
        }

        boolean activation = bool(environment, "aeg.enforcement-activation.control-plane-enabled");
        boolean revisionSync = bool(environment, "aeg.enforcement-activation.revision-sync-enabled");
        boolean wave0 = bool(environment, "aeg.enforcement-activation.wave0-read-pilot-enabled");
        boolean listPilot = bool(environment, "aeg.enforcement-activation.task-list-search-pilot-enabled");
        if (!activation && (revisionSync || wave0 || listPilot)) {
            blockers.add("Enforcement Activation sub-features require the control plane");
        }
        if (wave0 && !revisionSync) {
            blockers.add("Wave 0 read pilot requires revision synchronization");
        }
        if (listPilot && !wave0) {
            blockers.add("Task List/Search pilot requires Wave 0 read pilot");
        }
        // The control plane is an administrative surface. It may be available while the
        // decision runtime remains dormant. Synchronization and pilots are the authority hinge.
        boolean resourceDecisionAuthority = resourceAccess
                && bool(environment, "resource-access.decision-api-enabled");
        boolean activationRuntimeAuthority = revisionSync || wave0 || listPilot;
        if (activationRuntimeAuthority && !resourceDecisionAuthority) {
            blockers.add("Enforcement Activation runtime features require Resource Access decision authority");
        }
        return blockers;
    }

    private static boolean bool(Environment environment, String property) {
        return environment.getProperty(property, Boolean.class, false);
    }
}
