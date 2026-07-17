package com.opensocket.aievent.core.agent.operational;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.agent.eligibility.AgentDispatchEligibility;
import com.opensocket.aievent.core.agent.setup.AgentSetupReadinessResponse;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentOperationalView {
    private String tenantId;
    private String agentId;
    private boolean canReceiveTask;
    private String readinessStatus = "UNKNOWN";
    private String readinessLevel = "UNKNOWN";
    private String summary;
    private String firstBlockingCode;
    private String firstBlockingReason;
    private AgentOperationalNextAction nextAction;
    private List<AgentOperationalAuthorityCheck> authorityChecks = List.of();
    private AgentOperationalRuntimeSummary runtime = new AgentOperationalRuntimeSummary();
    private AgentSetupReadinessResponse setupReadiness;
    private AgentDispatchEligibility dispatchEligibility;
    private Map<String, Object> diagnostics = new LinkedHashMap<>();
    private OffsetDateTime generatedAt;

    public void setAuthorityChecks(List<AgentOperationalAuthorityCheck> authorityChecks) {
        this.authorityChecks = authorityChecks == null ? List.of() : List.copyOf(authorityChecks);
    }

    public void setDiagnostics(Map<String, Object> diagnostics) {
        this.diagnostics = diagnostics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(diagnostics);
    }
}
