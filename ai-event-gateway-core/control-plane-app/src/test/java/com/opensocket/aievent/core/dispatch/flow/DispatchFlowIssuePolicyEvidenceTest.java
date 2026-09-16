package com.opensocket.aievent.core.dispatch.flow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class DispatchFlowIssuePolicyEvidenceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void distinguishesOmittedFlowPolicyFromExplicitOptionalWithoutChangingHistoricalModelDefault() throws Exception {
        DispatchFlowView omitted = objectMapper.readValue("{}", DispatchFlowView.class);
        DispatchFlowView explicit = objectMapper.readValue("{\"defaultIssueSyncPolicy\":\"OPTIONAL\",\"evidenceMutationSource\":\"a2a-enterprise-bootstrap\"}", DispatchFlowView.class);
        DispatchFlowView explicitNull = objectMapper.readValue("{\"defaultIssueSyncPolicy\":null}", DispatchFlowView.class);

        assertThat(omitted.getDefaultIssueSyncPolicy()).isEqualTo("OPTIONAL");
        assertThat(omitted.defaultIssueSyncPolicyWasProvided()).isFalse();
        assertThat(DispatchFlowIssuePolicyEvidence.capture(omitted).flowPolicyProvided()).isFalse();
        assertThat(DispatchFlowIssuePolicyEvidence.display(false, omitted.getDefaultIssueSyncPolicy())).isEqualTo("<ABSENT>");

        assertThat(explicit.defaultIssueSyncPolicyWasProvided()).isTrue();
        assertThat(DispatchFlowIssuePolicyEvidence.capture(explicit).mutationSource()).isEqualTo("A2A-ENTERPRISE-BOOTSTRAP");
        assertThat(explicitNull.defaultIssueSyncPolicyWasProvided()).isTrue();
        assertThat(DispatchFlowIssuePolicyEvidence.display(true, explicitNull.getDefaultIssueSyncPolicy())).isEqualTo("<NULL>");
    }

    @Test
    void distinguishesOmittedRulePolicyFromExplicitRuleOverride() throws Exception {
        DispatchFlowRuleView omitted = objectMapper.readValue("{}", DispatchFlowRuleView.class);
        DispatchFlowRuleView explicit = objectMapper.readValue("{\"issueSyncPolicy\":\"REQUIRED\"}", DispatchFlowRuleView.class);

        assertThat(omitted.issueSyncPolicyWasProvided()).isFalse();
        assertThat(omitted.getIssueSyncPolicy()).isNull();
        assertThat(explicit.issueSyncPolicyWasProvided()).isTrue();
        assertThat(explicit.getIssueSyncPolicy()).isEqualTo("REQUIRED");
    }

    @Test
    void infersKnownMutationSourceOnlyWhenExplicitEvidenceHintIsAbsent() {
        DispatchFlowView flow = new DispatchFlowView();
        flow.getMetadata().put("enterpriseA2aDemo", true);
        assertThat(DispatchFlowIssuePolicyEvidence.mutationSource(flow)).isEqualTo("A2A_ENTERPRISE_BOOTSTRAP_INFERRED");

        flow.setEvidenceMutationSource("ADMIN_UI_ISSUE_POLICY_CHANGE");
        assertThat(DispatchFlowIssuePolicyEvidence.mutationSource(flow)).isEqualTo("ADMIN_UI_ISSUE_POLICY_CHANGE");
    }
}
