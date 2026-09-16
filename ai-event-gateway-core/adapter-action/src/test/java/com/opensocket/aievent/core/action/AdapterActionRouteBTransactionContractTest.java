package com.opensocket.aievent.core.action;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.integration.issue.automation.IssueAutomationActionCommand;

class AdapterActionRouteBTransactionContractTest {
    @Test
    void routeBActionRequestMustUseNestedSavepointSoOuterFinalizationCanPersistFailureEvidence() throws Exception {
        var method = AdapterActionService.class.getMethod("request", IssueAutomationActionCommand.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.NESTED);
    }
}
