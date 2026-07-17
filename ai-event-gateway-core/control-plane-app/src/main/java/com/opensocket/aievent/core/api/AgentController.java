package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentQuery;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityItem;
import com.opensocket.aievent.core.agent.AgentRuntimeDescriptor;
import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityProfile;
import com.opensocket.aievent.core.agent.AgentRuntimeLoadSnapshot;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentDirectoryService agentDirectoryService;

    public AgentController(AgentDirectoryService agentDirectoryService) {
        this.agentDirectoryService = agentDirectoryService;
    }

    @PostMapping("/register")
    public AgentSnapshot register(@RequestBody AgentSnapshot agent) {
        return agentDirectoryService.register(agent);
    }

    @PostMapping("/{agentId}/heartbeat")
    public AgentSnapshot heartbeat(@PathVariable String agentId, @RequestBody AgentHeartbeatRequest request) {
        return agentDirectoryService.heartbeat(agentId,
                request.status() == null ? AgentStatus.IDLE : request.status(),
                request.currentTaskCount(),
                request.healthScore() <= 0 ? 100 : request.healthScore());
    }

    @GetMapping
    public List<AgentSnapshot> search(@RequestParam(required = false) String siteId,
                                      @RequestParam(required = false) String ownerGatewayNodeId,
                                      @RequestParam(required = false) AgentStatus status,
                                      @RequestParam(required = false) List<String> requiredCapabilities,
                                      @RequestParam(defaultValue = "false") boolean assignableOnly,
                                      @RequestParam(defaultValue = "100") int limit) {
        AgentQuery query = new AgentQuery();
        query.setSiteId(siteId);
        query.setOwnerGatewayNodeId(ownerGatewayNodeId);
        query.setStatus(status);
        query.setRequiredCapabilities(requiredCapabilities);
        query.setAssignableOnly(assignableOnly);
        query.setLimit(limit);
        return agentDirectoryService.search(query);
    }

    @GetMapping("/available")
    public List<AgentSnapshot> available(@RequestParam(required = false) String siteId,
                                         @RequestParam(required = false) List<String> requiredCapabilities,
                                         @RequestParam(defaultValue = "100") int limit) {
        AgentQuery query = new AgentQuery();
        query.setSiteId(siteId);
        query.setRequiredCapabilities(requiredCapabilities);
        query.setAssignableOnly(true);
        query.setLimit(limit);
        return agentDirectoryService.search(query);
    }


    @GetMapping("/{agentId}/runtime/capability-profile")
    public AgentRuntimeCapabilityProfile runtimeCapabilityProfile(@PathVariable String agentId) {
        return agentDirectoryService.findRuntimeCapabilityProfile(agentId)
                .orElseThrow(() -> new StandardApiException(
                        StandardApiErrorCode.CORE_AGENT_NOT_FOUND, "Runtime capability profile not found: " + agentId));
    }

    @GetMapping("/{agentId}/runtime/descriptor")
    public AgentRuntimeDescriptor runtimeDescriptor(@PathVariable String agentId) {
        return agentDirectoryService.findRuntimeDescriptor(agentId)
                .orElseThrow(() -> new StandardApiException(
                        StandardApiErrorCode.CORE_AGENT_NOT_FOUND, "Runtime descriptor not found: " + agentId));
    }

    @GetMapping("/{agentId}/runtime/capabilities")
    public List<AgentRuntimeCapabilityItem> runtimeCapabilityItems(@PathVariable String agentId) {
        return agentDirectoryService.findRuntimeCapabilityItems(agentId);
    }

    @GetMapping("/{agentId}/runtime/load")
    public AgentRuntimeLoadSnapshot runtimeLoad(@PathVariable String agentId) {
        return agentDirectoryService.findRuntimeLoadSnapshot(agentId)
                .orElseThrow(() -> new StandardApiException(
                        StandardApiErrorCode.CORE_AGENT_NOT_FOUND, "Runtime load snapshot not found: " + agentId));
    }

    @GetMapping("/metadata")
    public AgentMetadata metadata() {
        return new AgentMetadata(agentDirectoryService.mode(), agentDirectoryService.runtimeStateMode(), AgentStatus.values(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    public record AgentHeartbeatRequest(AgentStatus status, int currentTaskCount, int healthScore) {}
    public record AgentMetadata(String storeMode, String runtimeStateMode, AgentStatus[] statuses, OffsetDateTime now) {}
}
