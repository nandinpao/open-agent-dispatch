package com.opensocket.aievent.core.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterExecutionAuthority;
import com.opensocket.aievent.core.dispatch.DispatchProperties;
import com.opensocket.aievent.core.integration.IntegrationEventProperties;
import com.opensocket.aievent.core.iam.runtime.config.EventIntakeSecurityProperties;
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
    private final EventIntakeSecurityProperties eventIntakeSecurity;
    private final CoreInternalSecurityProperties internalSecurity;
    private final DispatchProperties dispatchProperties;
    private final RecoveryGovernanceProperties recoveryGovernance;
    private final Environment environment;

    public CoreDeploymentModeValidator(CoreDeploymentProperties deployment,
                                       AdapterActionExecutionProperties adapterExecutor,
                                       IntegrationEventProperties integrationEvents,
                                       EventIntakeSecurityProperties eventIntakeSecurity,
                                       CoreInternalSecurityProperties internalSecurity,
                                       DispatchProperties dispatchProperties,
                                       RecoveryGovernanceProperties recoveryGovernance,
                                       Environment environment) {
        this.deployment = deployment;
        this.adapterExecutor = adapterExecutor;
        this.integrationEvents = integrationEvents;
        this.eventIntakeSecurity = eventIntakeSecurity == null ? new EventIntakeSecurityProperties() : eventIntakeSecurity;
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
            validateProductionEventIntakeSecurity();
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
        if (!issue.isConnectorRuntimeEnabled()) {
            throw new IllegalStateException(
                    "Production profile requires the canonical Issue Connector Runtime.");
        }
        if (!issue.isConnectorRuntimeRequired()) {
            throw new IllegalStateException(
                    "Production profile must require canonical Issue Connector Runtime execution.");
        }
        if (issue.getExecutionAuthority() != AdapterExecutionAuthority.CORE_GOVERNED) {
            throw new IllegalStateException(
                    "Production profile requires ISSUE_EXECUTION_AUTHORITY=CORE_GOVERNED");
        }
        if (!issue.isAutoExecutePending()) {
            throw new IllegalStateException(
                    "Production profile requires ISSUE_EXECUTOR_AUTO_EXECUTE_PENDING=true so canonical Issue actions cannot remain unexecuted");
        }
        if (!issue.isLinkProjectionReconciliationEnabled()) {
            throw new IllegalStateException(
                    "Production profile requires ISSUE_LINK_PROJECTION_RECONCILIATION_ENABLED=true so durable provider results can converge into TaskIssueLink without re-executing the provider operation");
        }
        if (issue.getLinkProjectionMaxAttempts() < 1) {
            throw new IllegalStateException(
                    "Production profile requires ISSUE_LINK_PROJECTION_MAX_ATTEMPTS>=1");
        }
        if (adapterExecutor.getExecutionTimeout() == null
                || adapterExecutor.getExecutionTimeout().isZero()
                || adapterExecutor.getExecutionTimeout().isNegative()) {
            throw new IllegalStateException(
                    "Production profile requires adapter-executor.execution-timeout to be positive");
        }
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



    private void validateProductionEventIntakeSecurity() {
        eventIntakeSecurity.validate();
        if (!eventIntakeSecurity.jwtRequired()) {
            throw new IllegalStateException(
                    "Production profile requires aeg.iam.event-intake.mode=JWT_REQUIRED");
        }
        if (!eventIntakeSecurity.isAuditEnabled()) {
            throw new IllegalStateException(
                    "Production profile requires aeg.iam.event-intake.audit-enabled=true");
        }

        String eventIntakeToken = internalSecurity.getEventIntakeToken();
        if (!eventIntakeToken.isBlank()) {
            if (eventIntakeToken.equals(internalSecurity.getOperatorToken())) {
                throw new IllegalStateException(
                        "Production Event Intake compatibility token must not equal the Operator token");
            }
            String clusterToken = environment.getProperty("CLUSTER_INTERNAL_TOKEN", "");
            if (!clusterToken.isBlank() && eventIntakeToken.equals(clusterToken.trim())) {
                throw new IllegalStateException(
                        "Production Event Intake compatibility token must not equal CLUSTER_INTERNAL_TOKEN");
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
            // JWT_REQUIRED is the authoritative production Event Intake path. A legacy Event Intake
            // internal token is optional and, when configured, is checked for isolation above.
            if (role == CoreInternalSecurityRole.EVENT_INGESTION && eventIntakeSecurity.jwtRequired()) {
                continue;
            }
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


    private record PersistentStoreRequirement(String propertyName, String expectedValue) {
    }
}
