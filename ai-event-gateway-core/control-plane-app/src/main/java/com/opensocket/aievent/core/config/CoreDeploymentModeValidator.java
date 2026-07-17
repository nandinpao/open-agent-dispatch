package com.opensocket.aievent.core.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.dispatch.DispatchProperties;
import com.opensocket.aievent.core.integration.IntegrationEventProperties;
import com.opensocket.aievent.core.security.CoreInternalSecurityProperties;
import com.opensocket.aievent.core.security.CoreInternalSecurityRole;

@Component
public class CoreDeploymentModeValidator implements ApplicationRunner {
    private static final List<PersistentStoreRequirement> PRODUCTION_STORE_REQUIREMENTS = List.of(
            new PersistentStoreRequirement("core.outbox.store", "MYBATIS"),
            new PersistentStoreRequirement("core.integration-events.store", "MYBATIS"),
            new PersistentStoreRequirement("event.decisions.store", "MYBATIS"),
            new PersistentStoreRequirement("event.dedup.store", "MYBATIS"),
            new PersistentStoreRequirement("event.dedup.snapshot-store", "MYBATIS"),
            new PersistentStoreRequirement("incident.store", "MYBATIS"),
            new PersistentStoreRequirement("incident.summary.store", "MYBATIS"),
            new PersistentStoreRequirement("task.store", "MYBATIS"),
            new PersistentStoreRequirement("task.callback.store", "MYBATIS"),
            new PersistentStoreRequirement("gateway-nodes.store", "MYBATIS"),
            new PersistentStoreRequirement("agent-directory.store", "MYBATIS"),
            new PersistentStoreRequirement("assignment.store", "MYBATIS"),
            new PersistentStoreRequirement("routing.decision-store", "MYBATIS"),
            new PersistentStoreRequirement("dispatch.request-store", "MYBATIS"),
            new PersistentStoreRequirement("adapter-actions.store", "MYBATIS"),
            new PersistentStoreRequirement("adapter-executor.audit.store", "MYBATIS")
    );

    private final CoreDeploymentProperties deployment;
    private final AdapterActionExecutionProperties adapterExecutor;
    private final IntegrationEventProperties integrationEvents;
    private final CoreInternalSecurityProperties internalSecurity;
    private final DispatchProperties dispatchProperties;
    private final RecoveryGovernanceProperties recoveryGovernance;
    private final Environment environment;

    public CoreDeploymentModeValidator(CoreDeploymentProperties deployment,
                                       AdapterActionExecutionProperties adapterExecutor,
                                       IntegrationEventProperties integrationEvents,
                                       CoreInternalSecurityProperties internalSecurity,
                                       DispatchProperties dispatchProperties,
                                       RecoveryGovernanceProperties recoveryGovernance,
                                       Environment environment) {
        this.deployment = deployment;
        this.adapterExecutor = adapterExecutor;
        this.integrationEvents = integrationEvents;
        this.internalSecurity = internalSecurity;
        this.dispatchProperties = dispatchProperties == null ? new DispatchProperties() : dispatchProperties;
        this.recoveryGovernance = recoveryGovernance == null ? new RecoveryGovernanceProperties() : recoveryGovernance;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (deployment.getMode() == CoreDeploymentProperties.Mode.HYBRID_ADAPTER_WORKER
                && !adapterExecutor.isExternalMode()) {
            throw new IllegalStateException(
                    "HYBRID_ADAPTER_WORKER requires adapter-executor.mode=external");
        }
        if (integrationEvents.isDeliveryEnabled()
                && "NONE".equalsIgnoreCase(integrationEvents.getSink())) {
            throw new IllegalStateException(
                    "Integration-event delivery is enabled but core.integration-events.sink=NONE");
        }
        if (isProdProfile()) {
            validateProductionAdapterExecutorBoundary();
            validateProductionIssueExecutorReadiness();
            validateProductionPersistentStores();
            validateProductionInternalSecurity();
            validateProductionDispatchClientBoundary();
            validateProductionRecoveryGovernance();
        }
    }

    private boolean isProdProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
    }

    private void validateProductionAdapterExecutorBoundary() {
        if (adapterExecutor.getMock().isEnabled()) {
            throw new IllegalStateException(
                    "Production profile must not enable adapter-executor.mock.enabled");
        }
        if (adapterExecutor.getMcp().isMockCompatible()) {
            throw new IllegalStateException(
                    "Production profile must not enable adapter-executor.mcp.mock-compatible");
        }
        String defaultVendor = adapterExecutor.getIssue().getDefaultVendor();
        if (defaultVendor != null && defaultVendor.equalsIgnoreCase("MOCK")) {
            throw new IllegalStateException(
                    "Production profile must not use ISSUE_EXECUTOR_DEFAULT_VENDOR=MOCK");
        }
        if (adapterExecutor.getIssue().isJiraMockEnabled()
                || adapterExecutor.getIssue().isRedmineMockEnabled()
                || adapterExecutor.getIssue().isGitlabMockEnabled()) {
            throw new IllegalStateException(
                    "Production profile must not enable mock-compatible issue executors");
        }
    }


    private void validateProductionIssueExecutorReadiness() {
        AdapterActionExecutionProperties.Issue issue = adapterExecutor.getIssue();
        String defaultVendor = issue.getDefaultVendor();
        if (containsVendor(defaultVendor, "JIRA")) {
            throw new IllegalStateException(
                    "Production profile cannot use ISSUE_EXECUTOR_DEFAULT_VENDOR=JIRA until a real Jira executor profile is configured");
        }
        boolean redmineRequired = containsVendor(defaultVendor, "REDMINE");
        boolean gitlabRequired = containsVendor(defaultVendor, "GITLAB");
        if (redmineRequired || issue.getRedmine().isEnabled()) {
            validateProductionRedmineExecutor(redmineRequired);
        }
        if (gitlabRequired || issue.getGitlab().isEnabled()) {
            validateProductionGitlabExecutor(gitlabRequired);
        }
        if (adapterExecutor.getExecutionTimeout() == null
                || adapterExecutor.getExecutionTimeout().isZero()
                || adapterExecutor.getExecutionTimeout().isNegative()) {
            throw new IllegalStateException(
                    "Production profile requires adapter-executor.execution-timeout to be positive");
        }
    }

    private void validateProductionRedmineExecutor(boolean requiredByDefaultVendor) {
        AdapterActionExecutionProperties.Redmine redmine = adapterExecutor.getIssue().getRedmine();
        if (!redmine.isEnabled()) {
            if (requiredByDefaultVendor) {
                throw new IllegalStateException(
                        "Production profile requires REDMINE_EXECUTOR_ENABLED=true when ISSUE_EXECUTOR_DEFAULT_VENDOR=REDMINE");
            }
            return;
        }
        if (isUnsafeProductionEndpoint(redmine.getBaseUrl())) {
            throw new IllegalStateException(
                    "Production profile requires REDMINE_EXECUTOR_BASE_URL to be an explicit non-local production endpoint");
        }
        if (isUnsafeProductionToken(redmine.getApiKey())) {
            throw new IllegalStateException(
                    "Production profile requires REDMINE_EXECUTOR_API_KEY to be non-empty and non-placeholder");
        }
        if (isBlankOrPlaceholder(redmine.getProjectId())) {
            throw new IllegalStateException(
                    "Production profile requires REDMINE_EXECUTOR_PROJECT_ID to be non-empty and non-placeholder");
        }
    }

    private void validateProductionGitlabExecutor(boolean requiredByDefaultVendor) {
        AdapterActionExecutionProperties.Gitlab gitlab = adapterExecutor.getIssue().getGitlab();
        if (!gitlab.isEnabled()) {
            if (requiredByDefaultVendor) {
                throw new IllegalStateException(
                        "Production profile requires GITLAB_EXECUTOR_ENABLED=true when ISSUE_EXECUTOR_DEFAULT_VENDOR=GITLAB");
            }
            return;
        }
        if (isUnsafeProductionEndpoint(gitlab.getBaseUrl())) {
            throw new IllegalStateException(
                    "Production profile requires GITLAB_EXECUTOR_BASE_URL to be an explicit non-local production endpoint");
        }
        if (isUnsafeProductionToken(gitlab.getPrivateToken())) {
            throw new IllegalStateException(
                    "Production profile requires GITLAB_EXECUTOR_PRIVATE_TOKEN to be non-empty and non-placeholder");
        }
        if (isBlankOrPlaceholder(gitlab.getProjectId())) {
            throw new IllegalStateException(
                    "Production profile requires GITLAB_EXECUTOR_PROJECT_ID to be non-empty and non-placeholder");
        }
        if (gitlab.getProjectId().contains("%2F") || gitlab.getProjectId().contains("%2f")) {
            throw new IllegalStateException(
                    "Production profile requires GITLAB_EXECUTOR_PROJECT_ID to be a numeric id or raw path like group/project; do not pre-encode '/' as %2F");
        }
    }

    private boolean containsVendor(String value, String vendor) {
        return value != null && vendor != null && value.toUpperCase().contains(vendor.toUpperCase());
    }

    private void validateProductionPersistentStores() {
        for (PersistentStoreRequirement requirement : PRODUCTION_STORE_REQUIREMENTS) {
            String actual = environment.getProperty(requirement.propertyName());
            if (actual == null || actual.isBlank()) {
                throw new IllegalStateException(
                        "Production profile requires " + requirement.propertyName()
                                + "=" + requirement.expectedValue());
            }
            if (!requirement.expectedValue().equalsIgnoreCase(actual)) {
                throw new IllegalStateException(
                        "Production profile requires " + requirement.propertyName()
                                + "=" + requirement.expectedValue()
                                + ", but was " + actual);
            }
        }
    }



    private void validateProductionInternalSecurity() {
        if (!internalSecurity.isEnabled()) {
            throw new IllegalStateException(
                    "Production profile requires core.security.internal.enabled=true");
        }
        if (!internalSecurity.isProtectApiMutations()) {
            throw new IllegalStateException(
                    "Production profile requires core.security.internal.protect-api-mutations=true");
        }
        if (internalSecurity.isPermitActuatorHealthInfo()) {
            throw new IllegalStateException(
                    "Production profile must not permit unauthenticated actuator health/info endpoints");
        }
        if (!internalSecurity.isAuditLogEnabled()) {
            throw new IllegalStateException(
                    "Production profile requires core.security.internal.audit-log-enabled=true");
        }
        if (internalSecurity.isAllowLegacyTokenHeader()) {
            throw new IllegalStateException(
                    "Production profile requires core.security.internal.allow-legacy-token-header=false");
        }
        for (CoreInternalSecurityRole role : CoreInternalSecurityRole.values()) {
            String token = internalSecurity.tokenFor(role);
            if (isUnsafeProductionToken(token)) {
                throw new IllegalStateException(
                        "Production profile requires a non-empty, non-placeholder internal token for role " + role.name());
            }
        }
    }


    private void validateProductionDispatchClientBoundary() {
        if (!dispatchProperties.getClient().isEnabled()) {
            return;
        }
        if (isUnsafeProductionToken(dispatchProperties.getClient().getInternalToken())) {
            throw new IllegalStateException(
                    "Production profile requires dispatch.client.internal-token/DISPATCH_INTERNAL_TOKEN "
                            + "to be non-empty and non-placeholder when dispatch client is enabled");
        }
        String defaultGatewayBaseUrl = dispatchProperties.getClient().getDefaultGatewayBaseUrl();
        if (isUnsafeProductionEndpoint(defaultGatewayBaseUrl)) {
            throw new IllegalStateException(
                    "Production profile requires dispatch.client.default-gateway-base-url "
                            + "to be an explicit non-local production endpoint");
        }
        dispatchProperties.getClient().getGatewayBaseUrls().forEach((gatewayId, baseUrl) -> {
            if (isUnsafeProductionEndpoint(baseUrl)) {
                throw new IllegalStateException(
                        "Production profile requires dispatch.client.gateway-base-urls[" + gatewayId
                                + "] to be an explicit non-local production endpoint");
            }
        });
    }


    private void validateProductionRecoveryGovernance() {
        if (!recoveryGovernance.isEnabled()) {
            throw new IllegalStateException(
                    "Production profile requires core.recovery.governance.enabled=true");
        }
        if (!recoveryGovernance.isRequireReason()) {
            throw new IllegalStateException(
                    "Production profile requires core.recovery.governance.require-reason=true");
        }
        if (recoveryGovernance.getMinReasonLength() < 12) {
            throw new IllegalStateException(
                    "Production profile requires core.recovery.governance.min-reason-length>=12");
        }
        if (!recoveryGovernance.isRequireConfirmation()) {
            throw new IllegalStateException(
                    "Production profile requires core.recovery.governance.require-confirmation=true");
        }
        if (recoveryGovernance.isAllowBodyOperatorIdOverride()) {
            throw new IllegalStateException(
                    "Production profile requires core.recovery.governance.allow-body-operator-id-override=false");
        }
        if (!recoveryGovernance.isRequireDualControlForHighRisk()) {
            throw new IllegalStateException(
                    "Production profile requires core.recovery.governance.require-dual-control-for-high-risk=true");
        }
        if (!recoveryGovernance.isForbidSelfApproval()) {
            throw new IllegalStateException(
                    "Production profile requires core.recovery.governance.forbid-self-approval=true");
        }
    }

    private boolean isUnsafeProductionToken(String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        String normalized = token.trim().toLowerCase();
        return normalized.equals("change-me")
                || normalized.equals("changeme")
                || normalized.equals("local-cluster-internal-token-change-me")
                || normalized.equals("local-dev-agent-token-change-me")
                || normalized.equals("dev-token")
                || normalized.equals("test-token")
                || normalized.equals("password")
                || normalized.equals("secret")
                || normalized.startsWith("<")
                || normalized.endsWith(">")
                || normalized.startsWith("replace-with")
                || normalized.contains("replace-with")
                || normalized.endsWith("change-me");
    }

    private boolean isUnsafeProductionEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return true;
        }
        String normalized = endpoint.trim().toLowerCase();
        return normalized.startsWith("<")
                || normalized.endsWith(">")
                || normalized.contains("change-me")
                || normalized.contains("replace-with")
                || normalized.contains("localhost")
                || normalized.contains("127.0.0.1")
                || normalized.contains("0.0.0.0");
    }


    private boolean isBlankOrPlaceholder(String value) {
        return isUnsafeProductionToken(value);
    }

    private record PersistentStoreRequirement(String propertyName, String expectedValue) {
    }
}
