package com.opensocket.aievent.core.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.InMemoryAgentGovernanceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentDirectoryDiscoveryFirstPersistenceTest {

    @Test
    void runtimeOnlyAgentIsIgnoredByCoreDirectorySnapshot() {
        var directory = new InMemoryAgentDirectoryRepository();
        var governance = new InMemoryAgentGovernanceRepository();
        var service = new AgentDirectoryService(directory);
        service.setAgentGovernanceRepository(governance);

        var runtimeOnly = runtimeSnapshot("agent-pending", "tenant-spoofed-by-runtime");

        var persisted = service.replaceGatewaySnapshot("gateway-node-001", List.of(runtimeOnly));

        assertTrue(persisted.isEmpty());
        assertTrue(directory.findById("agent-pending").isEmpty());
    }

    @Test
    void approvedGovernanceOverridesRuntimeClaimedTenant() {
        var directory = new InMemoryAgentDirectoryRepository();
        var governance = new InMemoryAgentGovernanceRepository();
        var service = new AgentDirectoryService(directory);
        service.setAgentGovernanceRepository(governance);

        AgentProfile profile = new AgentProfile();
        profile.setAgentId("agent-approved");
        profile.setTenantId("tenant-a");
        profile.setApprovalStatus(AgentApprovalStatus.APPROVED);
        profile.setEnabled(true);
        profile.setOwnerDepartmentId("dept-a");
        profile.setBusinessOwnerUserId("user-a");
        profile.setResponsibilityRoleId("responsibility-a");
        profile.setOwnershipReviewStatus("CURRENT");
        profile.setNextOwnershipReviewAt(OffsetDateTime.now().plusDays(30));
        governance.saveProfile(profile);

        var runtime = runtimeSnapshot("agent-approved", "tenant-spoofed-by-runtime");
        var persisted = service.replaceGatewaySnapshot("gateway-node-001", List.of(runtime));

        assertEquals(1, persisted.size());
        assertEquals("tenant-a", persisted.getFirst().getTenantId());
        assertEquals("tenant-a", directory.findById("agent-approved").orElseThrow().getTenantId());
    }

    private AgentSnapshot runtimeSnapshot(String agentId, String claimedTenant) {
        AgentSnapshot snapshot = new AgentSnapshot();
        snapshot.setAgentId(agentId);
        snapshot.setTenantId(claimedTenant);
        snapshot.setAgentType("CUSTOM");
        snapshot.setStatus(AgentStatus.IDLE);
        snapshot.setMaxConcurrentTasks(1);
        snapshot.setHealthScore(100);
        return snapshot;
    }
}
