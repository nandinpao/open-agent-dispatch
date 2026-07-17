package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.agent.governance.AgentSecurityEventType;
import org.junit.jupiter.api.Test;

class AgentGovernanceSecurityEventIngestionTest {
    @Test
    void p7DuplicateRuntimeEventTypesArePresent() {
        assertThat(AgentSecurityEventType.valueOf("DUPLICATE_RUNTIME_DETECTED")).isNotNull();
        assertThat(AgentSecurityEventType.valueOf("DUPLICATE_RUNTIME_AUTO_ENFORCED")).isNotNull();
        assertThat(AgentSecurityEventType.valueOf("DUPLICATE_RUNTIME_AUTO_ENFORCEMENT_FAILED")).isNotNull();
    }
}
