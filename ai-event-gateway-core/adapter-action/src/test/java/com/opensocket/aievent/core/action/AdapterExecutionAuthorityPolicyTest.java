package com.opensocket.aievent.core.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterExecutionAuthority;
import com.opensocket.aievent.core.action.executor.AdapterExecutionAuthorityPolicy;

class AdapterExecutionAuthorityPolicyTest {
    @Test
    void externalGlobalModeStillKeepsIssueTrackingInsideCore() {
        AdapterActionExecutionProperties properties = new AdapterActionExecutionProperties();
        properties.setMode("external");
        properties.setEnabled(false);
        properties.getIssue().setConnectorRuntimeEnabled(true);
        properties.getIssue().setExecutionAuthority(AdapterExecutionAuthority.CORE_GOVERNED);

        AdapterExecutionAuthorityPolicy policy = new AdapterExecutionAuthorityPolicy(properties);

        assertThat(policy.authorityFor(AdapterType.ISSUE_TRACKING)).isEqualTo(AdapterExecutionAuthority.CORE_GOVERNED);
        assertThat(policy.authorityFor(AdapterType.MCP)).isEqualTo(AdapterExecutionAuthority.EXTERNAL_WORKER);
        assertThat(policy.canCoreExecute(AdapterType.ISSUE_TRACKING)).isTrue();
        assertThat(policy.canExternalWorkerClaim(AdapterType.ISSUE_TRACKING)).isFalse();
        assertThat(policy.canExternalWorkerClaim(AdapterType.MCP)).isTrue();
    }

    @Test
    void issueTrackingCanNeverBeConfiguredForExternalWorkerAuthority() {
        AdapterActionExecutionProperties properties = new AdapterActionExecutionProperties();
        assertThatThrownBy(() -> properties.getIssue().setExecutionAuthority(AdapterExecutionAuthority.EXTERNAL_WORKER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be EXTERNAL_WORKER");
    }

    @Test
    void disabledIssueConnectorProducesExplicitExecutorNotAvailableCode() {
        AdapterActionExecutionProperties properties = new AdapterActionExecutionProperties();
        properties.getIssue().setConnectorRuntimeEnabled(false);
        AdapterExecutionAuthorityPolicy policy = new AdapterExecutionAuthorityPolicy(properties);

        assertThatThrownBy(() -> policy.requireCoreExecution(AdapterType.ISSUE_TRACKING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AdapterExecutionAuthorityPolicy.ISSUE_EXECUTOR_NOT_AVAILABLE);
    }
}
