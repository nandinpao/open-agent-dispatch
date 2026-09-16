package com.opensocket.aievent.core.agent.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class AgentCanonicalCapabilityAssignmentTest {

    @Test
    void adminAssignmentUsesActiveCanonicalCapabilityAndPreservesSemanticCode() {
        AgentAssignmentRepository repository = mock(AgentAssignmentRepository.class);
        AgentCapabilityCatalog canonical = new AgentCapabilityCatalog();
        canonical.setTenantId("tenant-a");
        canonical.setCapabilityCode("enterprise-application.erp-issue-diagnosis");
        canonical.setCapabilityName("ERP issue diagnosis");
        canonical.setStatus("ACTIVE");
        canonical.setVersion(3);

        when(repository.findCanonicalCapabilityByCode("tenant-a", "enterprise-application.erp-issue-diagnosis"))
                .thenReturn(Optional.of(canonical));
        when(repository.findAgentCapabilityAssignmentByAgentAndCapability("agent-erp-01", "enterprise-application.erp-issue-diagnosis"))
                .thenReturn(Optional.empty());
        when(repository.saveAgentCapabilityAssignment(any(AgentCapabilityAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AgentCapabilityCommand command = new AgentCapabilityCommand();
        command.setTenantId("tenant-a");
        command.setCapabilityCode("enterprise-application.erp-issue-diagnosis");
        command.setOperatorId("admin-ui");

        AgentCapabilityAssignment saved = new AgentAssignmentService(repository)
                .requestAgentCapability("agent-erp-01", command);

        assertThat(saved.getCapabilityCode()).isEqualTo("enterprise-application.erp-issue-diagnosis");
        assertThat(saved.getCapabilityName()).isEqualTo("ERP issue diagnosis");
        assertThat(saved.getStatus()).isEqualTo(AgentCapabilityAssignmentStatus.APPROVED);
        assertThat(saved.getMetadata()).containsEntry("capabilityAuthority", "CAPABILITY_DEFINITIONS");
        verify(repository).findCanonicalCapabilityByCode("tenant-a", "enterprise-application.erp-issue-diagnosis");
    }
}
