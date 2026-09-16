package com.opensocket.aievent.core.dispatch.flow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DispatchFlowCanonicalCapabilityNormalizationTest {

    @Test
    void canonicalCapabilityCodePreservesLowerCaseDottedSemanticIdentity() {
        DispatchFlowRequiredSkillView request = new DispatchFlowRequiredSkillView();
        request.setCapabilityCode("enterprise-application.erp-issue-diagnosis");
        request.setCapabilityName("ERP issue diagnosis");
        request.setCapabilityKind("SERVICE");

        DispatchFlowRequiredSkillView normalized = DispatchFlowNormalizationSupport.normalizeCapability(
                "tenant-a", "flow-erp", request);

        assertThat(normalized.getCapabilityCode()).isEqualTo("enterprise-application.erp-issue-diagnosis");
        assertThat(normalized.getCapabilityName()).isEqualTo("ERP issue diagnosis");
        assertThat(normalized.getId()).contains("enterprise-application-erp-issue-diagnosis");
    }
}
