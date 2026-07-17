package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.governance.AgentSecurityEnforcementMode;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEventType;

class AgentGovernanceSecurityPolicyTest {
    @Test
    void p8SecurityPolicyEnumsShouldExist() {
        assertThat(AgentSecurityEnforcementMode.valueOf("ALERT_ONLY")).isNotNull();
        assertThat(AgentSecurityEnforcementMode.valueOf("QUARANTINE_AND_DISCONNECT")).isNotNull();
        assertThat(AgentSecurityEnforcementMode.valueOf("QUARANTINE_REVOKE_AND_DISCONNECT")).isNotNull();
        assertThat(AgentSecurityEventType.valueOf("DUPLICATE_RUNTIME_POLICY_EVALUATED")).isNotNull();
        assertThat(AgentSecurityEventType.valueOf("SECURITY_NOTIFICATION_QUEUED")).isNotNull();
    }
}
