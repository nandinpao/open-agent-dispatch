package com.opensocket.aievent.core.agent.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class AgentRuntimeBindingIdempotencyTest {

    @Test
    void upsertRuntimeBindingShouldReuseExistingActiveTenantAgentBinding() {
        AgentAssignmentRepository repository = mock(AgentAssignmentRepository.class);
        Map<String, AgentRuntimeBinding> activeBindings = new LinkedHashMap<>();

        when(repository.findRuntimeResourceByCode(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.saveRuntimeResource(any(RuntimeResource.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findActiveRuntimeBindingByTenantAndAgent(anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(activeBindings.get(bindingKey(invocation.getArgument(0), invocation.getArgument(1)))));
        when(repository.saveRuntimeBinding(any(AgentRuntimeBinding.class))).thenAnswer(invocation -> {
            AgentRuntimeBinding binding = invocation.getArgument(0);
            activeBindings.put(bindingKey(binding.getTenantId(), binding.getAgentId()), binding);
            return binding;
        });
        when(repository.findRuntimeBindingsByAgent(anyString(), anyString())).thenAnswer(invocation -> {
            String agentId = invocation.getArgument(0);
            String status = invocation.getArgument(1);
            return activeBindings.values().stream()
                    .filter(binding -> agentId.equals(binding.getAgentId()))
                    .filter(binding -> status == null || status.equalsIgnoreCase(binding.getBindingStatus()))
                    .toList();
        });

        AgentAssignmentService service = new AgentAssignmentService(repository);
        RuntimeResource runtime = new RuntimeResource();
        runtime.setTenantId("tenant-a");
        runtime.setRuntimeId("runtime-agent-local-001");
        runtime.setRuntimeCode("agent-local-001-runtime");
        runtime.setRuntimeName("agent-local-001 Runtime");
        runtime.setRuntimeType("Docker");
        runtime.setTrustStatus("TRUSTED");
        runtime.setStatus("ACTIVE");
        runtime.setCapacityLimit(3);
        service.upsertRuntimeResource(runtime);

        AgentRuntimeBinding firstRequest = bindingRequest("tenant-a", "runtime-agent-local-001", "agent-local-001-runtime", 3);
        AgentRuntimeBinding first = service.upsertRuntimeBinding("agent-local-001", firstRequest);
        String bindingId = first.getBindingId();

        AgentRuntimeBinding secondRequest = bindingRequest("tenant-a", "runtime-agent-local-001", "agent-local-001-runtime", 5);
        AgentRuntimeBinding second = service.upsertRuntimeBinding("agent-local-001", secondRequest);

        assertThat(second.getBindingId()).isEqualTo(bindingId);
        assertThat(second.getTenantId()).isEqualTo("tenant-a");
        assertThat(second.getAgentId()).isEqualTo("agent-local-001");
        assertThat(second.getBindingStatus()).isEqualTo("ACTIVE");
        assertThat(second.getCapacityLimit()).isEqualTo(5);
        assertThat(service.findRuntimeBindingsByAgent("agent-local-001", "ACTIVE")).hasSize(1);
    }

    private String bindingKey(String tenantId, String agentId) {
        return tenantId + "::" + agentId;
    }

    private AgentRuntimeBinding bindingRequest(String tenantId, String runtimeId, String runtimeCode, int capacityLimit) {
        AgentRuntimeBinding binding = new AgentRuntimeBinding();
        binding.setTenantId(tenantId);
        binding.setRuntimeId(runtimeId);
        binding.setRuntimeCode(runtimeCode);
        binding.setBindingStatus("ACTIVE");
        binding.setVerifiedBy("stage8-f0e-test");
        binding.setApprovedBy("stage8-f0e-test");
        binding.setCapacityLimit(capacityLimit);
        binding.setDataScope("STANDARD");
        binding.setRiskLimit("MIDDLE");
        return binding;
    }
}
