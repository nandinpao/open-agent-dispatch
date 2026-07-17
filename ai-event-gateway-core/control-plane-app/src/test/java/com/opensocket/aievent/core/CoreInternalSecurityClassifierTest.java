package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.opensocket.aievent.core.security.CoreInternalSecurityProperties;
import com.opensocket.aievent.core.security.CoreInternalSecurityRequestClassifier;
import com.opensocket.aievent.core.security.CoreInternalSecurityRole;

class CoreInternalSecurityClassifierTest {
    private final CoreInternalSecurityProperties properties = new CoreInternalSecurityProperties();
    private final CoreInternalSecurityRequestClassifier classifier = new CoreInternalSecurityRequestClassifier(properties);

    @Test
    void shouldRequireGatewayRoleForGatewayDirectoryAndTaskCallbacks() {
        assertThat(classifier.requiredRole(request("POST", "/internal/gateway-nodes/register")))
                .contains(CoreInternalSecurityRole.GATEWAY);
        assertThat(classifier.requiredRole(request("POST", "/internal/gateway-nodes/gateway-node-001/agents/redmine-agent-001/connected")))
                .contains(CoreInternalSecurityRole.GATEWAY);
        assertThat(classifier.requiredRole(request("POST", "/internal/gateway-nodes/gateway-node-001/agents/redmine-agent-001/heartbeat")))
                .contains(CoreInternalSecurityRole.GATEWAY);
        assertThat(classifier.requiredRole(request("POST", "/internal/gateway-nodes/gateway-node-001/agents/redmine-agent-001/disconnected")))
                .contains(CoreInternalSecurityRole.GATEWAY);
        assertThat(classifier.requiredRole(request("POST", "/internal/control-plane/tasks/task-001/result")))
                .contains(CoreInternalSecurityRole.GATEWAY);
        assertThat(classifier.requiredRole(request("POST", "/internal/agents/authorize-connection")))
                .contains(CoreInternalSecurityRole.GATEWAY);
        assertThat(classifier.requiredRole(request("POST", "/internal/agents/security-events")))
                .contains(CoreInternalSecurityRole.GATEWAY);
    }


    @Test
    void shouldSeparateAdapterWorkerAndOperatorRoles() {
        assertThat(classifier.requiredRole(request("POST", "/internal/adapter-actions/claim")))
                .contains(CoreInternalSecurityRole.ADAPTER_WORKER);
        assertThat(classifier.requiredRole(request("POST", "/internal/adapter-actions/recover-expired-leases")))
                .contains(CoreInternalSecurityRole.OPERATOR);
    }

    @Test
    void shouldProtectActuatorExceptHealthAndInfoWhenPermitted() {
        properties.setPermitActuatorHealthInfo(true);
        assertThat(classifier.requiredRole(request("GET", "/actuator/health"))).isEmpty();
        assertThat(classifier.requiredRole(request("GET", "/actuator/info"))).isEmpty();
        assertThat(classifier.requiredRole(request("GET", "/actuator/prometheus")))
                .contains(CoreInternalSecurityRole.ACTUATOR);
    }

    @Test
    void shouldOptionallyProtectApiMutationsAsOperator() {
        assertThat(classifier.requiredRole(request("POST", "/api/dispatch-requests/dr-001/execute"))).isEmpty();
        properties.setProtectApiMutations(true);
        assertThat(classifier.requiredRole(request("POST", "/api/dispatch-requests/dr-001/execute")))
                .contains(CoreInternalSecurityRole.OPERATOR);
        assertThat(classifier.requiredRole(request("GET", "/api/dispatch-requests"))).isEmpty();
    }

    @Test
    void shouldSeparateActionGovernanceOperatorAdminAndApproverRoles() {
        assertThat(classifier.requiredRole(request("GET", "/admin/dispatch-governance/actions/catalog")))
                .contains(CoreInternalSecurityRole.OPERATOR);
        assertThat(classifier.requiredRole(request("POST", "/admin/dispatch-governance/actions/proposals")))
                .contains(CoreInternalSecurityRole.RECOVERY_OPERATOR);
        assertThat(classifier.requiredRole(request("PUT", "/admin/dispatch-governance/actions/catalog/ACTION_RANDOM")))
                .contains(CoreInternalSecurityRole.RECOVERY_ADMIN);
        assertThat(classifier.requiredRole(request("POST", "/admin/dispatch-governance/actions/grants/grant-random/approve")))
                .contains(CoreInternalSecurityRole.RECOVERY_APPROVER);
        assertThat(classifier.requiredRole(request("POST", "/admin/dispatch-governance/actions/approval-requests/request-random/decide")))
                .contains(CoreInternalSecurityRole.RECOVERY_APPROVER);
    }

    @Test
    void shouldProtectAdminApisAsOperator() {
        assertThat(classifier.requiredRole(request("GET", "/admin/agents")))
                .contains(CoreInternalSecurityRole.OPERATOR);
        assertThat(classifier.requiredRole(request("POST", "/admin/agent-enrollments/enroll-001/approve")))
                .contains(CoreInternalSecurityRole.OPERATOR);
        assertThat(classifier.requiredRole(request("POST", "/admin/agents/setup")))
                .contains(CoreInternalSecurityRole.OPERATOR);
        assertThat(classifier.requiredRole(request("GET", "/admin/agents/redmine-agent-001/setup-readiness")))
                .contains(CoreInternalSecurityRole.OPERATOR);
        assertThat(classifier.requiredRole(request("GET", "/admin/agents/redmine-agent-001/latest-auth-failure")))
                .contains(CoreInternalSecurityRole.OPERATOR);
        assertThat(classifier.requiredRole(request("GET", "/admin/agents/redmine-agent-001/connection-repair-actions")))
                .contains(CoreInternalSecurityRole.OPERATOR);
        assertThat(classifier.requiredRole(request("POST", "/admin/agents/redmine-agent-001/connection-repair-actions/ROTATE_CREDENTIAL")))
                .contains(CoreInternalSecurityRole.OPERATOR);
        assertThat(classifier.requiredRole(request("POST", "/admin/dispatch-requests/dr-001/retry")))
                .contains(CoreInternalSecurityRole.OPERATOR);
        assertThat(classifier.requiredRole(request("POST", "/admin/tasks/task-001/cancel")))
                .contains(CoreInternalSecurityRole.OPERATOR);
    }

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }
}
