package com.opensocket.aievent.gateway.netty;

import java.util.Arrays;
import java.util.Locale;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.config.AgentProperties;
import com.opensocket.aievent.gateway.netty.config.AuditLogProperties;
import com.opensocket.aievent.gateway.netty.config.ConnectionProtectionProperties;
import com.opensocket.aievent.gateway.netty.config.CoreDirectorySyncProperties;
import com.opensocket.aievent.gateway.netty.config.CoreForwardProperties;
import com.opensocket.aievent.gateway.netty.config.CoreTaskCallbackRelayProperties;
import com.opensocket.aievent.gateway.netty.config.TaskAssignmentProperties;
import com.opensocket.aievent.gateway.netty.authorization.CoreAgentAuthorizationProperties;

/**
 * Fail-fast guard for production Netty deployments.
 *
 * <p>Local and test profiles may use relaxed defaults. Production must not start with disabled
 * Admin authentication, disabled Agent authentication, disabled connection protection, missing
 * service tokens, a NOOP audit sink, or a task-assignment mode that makes Netty the business
 * assignment authority.</p>
 */
@Component
public class NettyProductionDeploymentValidator implements ApplicationRunner {
    private final Environment environment;
    private final AdminProperties adminProperties;
    private final AgentProperties agentProperties;
    private final ConnectionProtectionProperties connectionProtectionProperties;
    private final AuditLogProperties auditLogProperties;
    private final CoreDirectorySyncProperties directorySyncProperties;
    private final CoreTaskCallbackRelayProperties callbackRelayProperties;
    private final CoreForwardProperties coreForwardProperties;
    private final TaskAssignmentProperties taskAssignmentProperties;
    private final CoreAgentAuthorizationProperties agentAuthorizationProperties;

    public NettyProductionDeploymentValidator(Environment environment,
                                              AdminProperties adminProperties,
                                              AgentProperties agentProperties,
                                              ConnectionProtectionProperties connectionProtectionProperties,
                                              AuditLogProperties auditLogProperties,
                                              CoreDirectorySyncProperties directorySyncProperties,
                                              CoreTaskCallbackRelayProperties callbackRelayProperties,
                                              CoreForwardProperties coreForwardProperties,
                                              TaskAssignmentProperties taskAssignmentProperties,
                                              CoreAgentAuthorizationProperties agentAuthorizationProperties) {
        this.environment = environment;
        this.adminProperties = adminProperties;
        this.agentProperties = agentProperties;
        this.connectionProtectionProperties = connectionProtectionProperties;
        this.auditLogProperties = auditLogProperties;
        this.directorySyncProperties = directorySyncProperties;
        this.callbackRelayProperties = callbackRelayProperties;
        this.coreForwardProperties = coreForwardProperties;
        this.taskAssignmentProperties = taskAssignmentProperties;
        this.agentAuthorizationProperties = agentAuthorizationProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProd()) {
            return;
        }
        validateAdminSecurity();
        validateAgentSecurity();
        validateConnectionProtection();
        validateAuditSink();
        validateTaskAssignmentBoundary();
        validateCoreAuthorization();
        validateCoreIntegrationTokens();
    }

    private boolean isProd() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
    }

    private void validateAdminSecurity() {
        if (!adminProperties.isMachineAuthEnabled()) {
            throw new IllegalStateException("Production profile requires admin.machine-auth-enabled=true");
        }
        if (!adminProperties.isMachineWebSocketHandshakeAuthEnabled()) {
            throw new IllegalStateException("Production profile requires admin.machine-web-socket-handshake-auth-enabled=true");
        }
        if (isUnsafeToken(adminProperties.getMachineToken())) {
            throw new IllegalStateException("Production profile requires NETTY_MACHINE_ADMIN_TOKEN to be non-empty and non-placeholder");
        }
        if (isUnsafeToken(adminProperties.getInternalToken())) {
            throw new IllegalStateException("Production profile requires CLUSTER_INTERNAL_TOKEN/admin.internal-token to be non-empty and non-placeholder");
        }
        if (adminProperties.getMachineToken().equals(adminProperties.getInternalToken())) {
            throw new IllegalStateException("NETTY_MACHINE_ADMIN_TOKEN and CLUSTER_INTERNAL_TOKEN must be role-separated");
        }
    }

    private void validateAgentSecurity() {
        if (!agentProperties.isAuthEnabled()) {
            throw new IllegalStateException("Production profile requires agent.auth-enabled=true");
        }
        if (!agentProperties.isWebSocketHandshakeAuthEnabled()) {
            throw new IllegalStateException("Production profile requires agent.web-socket-handshake-auth-enabled=true");
        }
        if (isUnsafeToken(agentProperties.getOnboardingToken())) {
            throw new IllegalStateException("Production profile requires AGENT_ONBOARDING_TOKEN to be non-empty and non-placeholder");
        }
    }

    private void validateConnectionProtection() {
        if (!connectionProtectionProperties.isEnabled()) {
            throw new IllegalStateException("Production profile requires connection-protection.enabled=true");
        }
    }

    private void validateAuditSink() {
        if (!auditLogProperties.isPersistenceEnabled()) {
            throw new IllegalStateException("Production profile requires audit.persistence-enabled=true");
        }
        String sink = auditLogProperties.getSink() == null ? "" : auditLogProperties.getSink().trim().toUpperCase(Locale.ROOT);
        if (sink.isBlank() || "NOOP".equals(sink)) {
            throw new IllegalStateException("Production profile must not use audit.sink=NOOP");
        }
    }

    private void validateTaskAssignmentBoundary() {
        if (!taskAssignmentProperties.coreOnly() && !taskAssignmentProperties.disabled()) {
            throw new IllegalStateException("Production profile supports only gateway.task-assignment.mode=core-only or disabled");
        }
        if (!taskAssignmentProperties.isRejectExternalTaskDispatch()) {
            throw new IllegalStateException("Production profile requires gateway.task-assignment.reject-external-task-dispatch=true");
        }
    }

    private void validateCoreAuthorization() {
        if (!agentAuthorizationProperties.isEnabled()) {
            throw new IllegalStateException("Production profile requires gateway.agent-authorization.enabled=true");
        }
        if (!agentAuthorizationProperties.isFailClosed()) {
            throw new IllegalStateException("Production profile requires gateway.agent-authorization.fail-closed=true");
        }
        if (isUnsafeToken(agentAuthorizationProperties.getAuthToken())) {
            throw new IllegalStateException("Production profile requires gateway.agent-authorization.auth-token to be non-empty and non-placeholder");
        }
    }

    private void validateCoreIntegrationTokens() {
        if (!directorySyncProperties.isEnabled()) {
            throw new IllegalStateException("Production profile requires gateway.core-directory-sync.enabled=true");
        }
        if (!callbackRelayProperties.isEnabled()) {
            throw new IllegalStateException("Production profile requires gateway.core-task-callback-relay.enabled=true");
        }
        if (!callbackRelayProperties.isRequireDispatchContext()) {
            throw new IllegalStateException("Production profile requires gateway.core-task-callback-relay.require-dispatch-context=true");
        }
        if (!callbackRelayProperties.isRequireAssignmentId()) {
            throw new IllegalStateException("Production profile requires gateway.core-task-callback-relay.require-assignment-id=true");
        }
        if (isUnsafeToken(directorySyncProperties.getAuthToken())) {
            throw new IllegalStateException("Production profile requires gateway.core-directory-sync.auth-token to be non-empty and non-placeholder");
        }
        if (isUnsafeToken(callbackRelayProperties.getAuthToken())) {
            throw new IllegalStateException("Production profile requires gateway.core-task-callback-relay.auth-token to be non-empty and non-placeholder");
        }
        if (coreForwardProperties.isEnabled() && isUnsafeToken(coreForwardProperties.getAuthToken())) {
            throw new IllegalStateException("Production profile requires gateway.core-forward.auth-token when gateway.core-forward.enabled=true");
        }
    }

    private boolean isUnsafeToken(String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("change-me")
                || normalized.equals("changeme")
                || normalized.equals("local-cluster-internal-token-change-me")
                || normalized.equals("local-dev-agent-token-change-me")
                || normalized.equals("dev-token")
                || normalized.equals("test-token")
                || normalized.equals("password")
                || normalized.equals("admin")
                || normalized.equals("secret")
                || normalized.startsWith("<")
                || normalized.endsWith(">")
                || normalized.startsWith("replace-with")
                || normalized.contains("replace-with")
                || normalized.endsWith("change-me");
    }
}
