package com.opensocket.aievent.gateway.netty;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.config.AgentProperties;
import com.opensocket.aievent.gateway.netty.config.AuditLogProperties;
import com.opensocket.aievent.gateway.netty.config.ConnectionProtectionProperties;
import com.opensocket.aievent.gateway.netty.config.CoreDirectorySyncProperties;
import com.opensocket.aievent.gateway.netty.config.CoreForwardProperties;
import com.opensocket.aievent.gateway.netty.config.CoreTaskCallbackRelayProperties;
import com.opensocket.aievent.gateway.netty.config.TaskAssignmentProperties;
import com.opensocket.aievent.gateway.netty.authorization.CoreAgentAuthorizationProperties;

class NettyProductionDeploymentValidatorTest {

    @Test
    void shouldRejectDisabledMachineAdminAuthInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.admin.setMachineAuthEnabled(false);
        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectMissingMachineAdminTokenInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.admin.setMachineToken("");
        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectDisabledMachineWebSocketHandshakeAuthInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.admin.setMachineWebSocketHandshakeAuthEnabled(false);
        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectMissingClusterInternalTokenInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.admin.setInternalToken("");
        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectSharedMachineAndClusterCredentialInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.admin.setInternalToken(fixture.admin.getMachineToken());
        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectPlaceholderMachineAdminTokenInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.admin.setMachineToken("local-admin-api-token-change-me");
        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectAngleBracketMachineAdminTokenInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.admin.setMachineToken("<change-me>");
        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectReplaceWithAgentOnboardingTokenInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.agent.setOnboardingToken("replace-with-prod-agent-token");

        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectNoopAuditSinkInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.audit.setSink("NOOP");

        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectPlaceholderClusterTokenInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.directory.setAuthToken("change-me");

        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectDisabledAgentWebSocketHandshakeAuthInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.agent.setWebSocketHandshakeAuthEnabled(false);

        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectExternalTaskDispatchAllowedInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.taskAssignment.setRejectExternalTaskDispatch(false);

        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectMissingCoreForwardTokenWhenForwarderEnabledInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.coreForward.setEnabled(true);
        fixture.coreForward.setAuthToken("");

        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectDisabledCoreAgentAuthorizationInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.agentAuthorization.setEnabled(false);

        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRequireAssignmentIdForCallbackRelayInProdProfile() {
        TestFixture fixture = productionFixture();
        fixture.callback.setRequireAssignmentId(false);

        assertThrows(IllegalStateException.class, () -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldAllowHardenedProdConfiguration() {
        TestFixture fixture = productionFixture();

        assertDoesNotThrow(() -> fixture.validator().run(new DefaultApplicationArguments(new String[0])));
    }

    private TestFixture productionFixture() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        AdminProperties admin = new AdminProperties();
        admin.setMachineAuthEnabled(true);
        admin.setMachineToken("netty-machine-token-123");
        admin.setInternalToken("cluster-token-123");
        admin.setMachineWebSocketHandshakeAuthEnabled(true);

        AgentProperties agent = new AgentProperties();
        agent.setAuthEnabled(true);
        agent.setWebSocketHandshakeAuthEnabled(true);
        agent.setOnboardingToken("agent-onboarding-token-123");

        ConnectionProtectionProperties protection = new ConnectionProtectionProperties();
        protection.setEnabled(true);

        AuditLogProperties audit = new AuditLogProperties();
        audit.setPersistenceEnabled(true);
        audit.setSink("FILE");

        CoreDirectorySyncProperties directory = new CoreDirectorySyncProperties();
        directory.setEnabled(true);
        directory.setAuthToken("cluster-token-123");

        CoreTaskCallbackRelayProperties callback = new CoreTaskCallbackRelayProperties();
        callback.setEnabled(true);
        callback.setRequireDispatchContext(true);
        callback.setRequireAssignmentId(true);
        callback.setAuthToken("cluster-token-123");

        CoreForwardProperties coreForward = new CoreForwardProperties();
        coreForward.setEnabled(false);
        coreForward.setAuthToken("cluster-token-123");

        TaskAssignmentProperties taskAssignment = new TaskAssignmentProperties();
        taskAssignment.setRejectExternalTaskDispatch(true);

        CoreAgentAuthorizationProperties agentAuthorization = new CoreAgentAuthorizationProperties();
        agentAuthorization.setEnabled(true);
        agentAuthorization.setFailClosed(true);
        agentAuthorization.setAuthToken("cluster-token-123");

        return new TestFixture(environment, admin, agent, protection, audit, directory, callback, coreForward, taskAssignment, agentAuthorization);
    }

    private record TestFixture(MockEnvironment environment,
                               AdminProperties admin,
                               AgentProperties agent,
                               ConnectionProtectionProperties protection,
                               AuditLogProperties audit,
                               CoreDirectorySyncProperties directory,
                               CoreTaskCallbackRelayProperties callback,
                               CoreForwardProperties coreForward,
                               TaskAssignmentProperties taskAssignment,
                               CoreAgentAuthorizationProperties agentAuthorization) {
        NettyProductionDeploymentValidator validator() {
            return new NettyProductionDeploymentValidator(
                    environment,
                    admin,
                    agent,
                    protection,
                    audit,
                    directory,
                    callback,
                    coreForward,
                    taskAssignment,
                    agentAuthorization
            );
        }
    }
}
