package com.opensocket.aievent.gateway.netty.agent;

import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentSummaryResponse;
import com.opensocket.aievent.gateway.netty.api.GatewayApiErrorCode;
import com.opensocket.aievent.gateway.netty.api.GatewayApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent domain component for Agent Controller. It manages the lifecycle, status, connection
 * identity, and query model used by the Admin UI and command delivery diagnostics.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentRegistry agentRegistry;

    public AgentController(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    @GetMapping
    public List<AgentResponse> listAgents() {
        return agentRegistry.list().stream()
                .map(AgentResponse::from)
                .toList();
    }

    @GetMapping("/{agentId}")
    public AgentResponse getAgent(@PathVariable String agentId) {
        return agentRegistry.findById(agentId)
                .map(AgentResponse::from)
                .orElseThrow(() -> new GatewayApiException(
                        GatewayApiErrorCode.GATEWAY_AGENT_NOT_FOUND,
                        "Agent not found: " + agentId));
    }

    @GetMapping("/summary")
    public AgentSummaryResponse getSummary() {
        Map<String, Long> byStatus = agentRegistry.countGroupByStatus().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));

        return new AgentSummaryResponse(
                agentRegistry.count(),
                agentRegistry.countByStatus(AgentStatus.ONLINE),
                agentRegistry.countByStatus(AgentStatus.IDLE),
                agentRegistry.countByStatus(AgentStatus.BUSY),
                agentRegistry.countByStatus(AgentStatus.OFFLINE),
                agentRegistry.countByStatus(AgentStatus.TIMEOUT),
                agentRegistry.countByStatus(AgentStatus.ERROR),
                byStatus
        );
    }
}
