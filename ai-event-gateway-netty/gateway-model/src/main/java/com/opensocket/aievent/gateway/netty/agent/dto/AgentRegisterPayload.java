package com.opensocket.aievent.gateway.netty.agent.dto;

import com.opensocket.aievent.gateway.netty.agent.AgentType;
import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record AgentRegisterPayload(
        @NotBlank String agentId,
        @NotNull AgentType agentType,
        @NotNull ConnectionType connectionType,
        List<String> capabilities,
        Map<String, Object> capabilityProfile,
        Map<String, Object> metadata,
        String onboardingToken
) {
    public AgentRegisterPayload(
            String agentId,
            AgentType agentType,
            ConnectionType connectionType,
            List<String> capabilities,
            Map<String, Object> metadata
    ) {
        this(agentId, agentType, connectionType, capabilities, Map.of(), metadata, null);
    }


    public AgentRegisterPayload(
            String agentId,
            AgentType agentType,
            ConnectionType connectionType,
            List<String> capabilities,
            Map<String, Object> metadata,
            String onboardingToken
    ) {
        this(agentId, agentType, connectionType, capabilities, Map.of(), metadata, onboardingToken);
    }

    public AgentRegisterPayload {
        if (capabilities == null) {
            capabilities = List.of();
        }
        if (capabilityProfile == null) {
            capabilityProfile = Map.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
        if (onboardingToken != null && onboardingToken.isBlank()) {
            onboardingToken = null;
        }
    }

    /**
     * Compatibility accessor used by gateway authentication code.
     *
     * <p>Newer Agents may send a top-level onboardingToken. Existing Agents may
     * keep sending token-like values inside metadata. This accessor preserves
     * both contracts while keeping the historical five-argument constructor
     * available for existing tests and callers.</p>
     */
    @Override
    public String onboardingToken() {
        if (onboardingToken != null && !onboardingToken.isBlank()) {
            return onboardingToken.trim();
        }
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object token = metadata.get("onboardingToken");
        if (token == null || token.toString().isBlank()) {
            token = metadata.get("agentToken");
        }
        if (token == null || token.toString().isBlank()) {
            token = metadata.get("token");
        }
        return token == null ? null : token.toString().trim();
    }
}
