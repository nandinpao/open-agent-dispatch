package com.opensocket.aievent.core.routing.selection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingMember;
import com.opensocket.aievent.core.routing.AgentCandidateScore;

/** Immutable input used by Agent selection strategies. */
public record SelectionStrategyContext(
        String targetPoolId,
        String targetPoolCode,
        String selectionStrategy,
        Map<String, AgentPoolRoutingMember> membersByAgentId) {

    public SelectionStrategyContext {
        membersByAgentId = membersByAgentId == null ? Map.of() : Map.copyOf(membersByAgentId);
    }

    public boolean hasTargetPool() {
        return targetPoolId != null && !targetPoolId.isBlank();
    }

    public AgentPoolRoutingMember member(String agentId) {
        if (agentId == null || agentId.isBlank() || membersByAgentId == null || membersByAgentId.isEmpty()) {
            return null;
        }
        return membersByAgentId.get(normalizeAgentId(agentId));
    }

    public int memberPriority(String agentId) {
        AgentPoolRoutingMember member = member(agentId);
        return member == null ? 100 : member.getPriority();
    }

    public int memberWeight(String agentId) {
        AgentPoolRoutingMember member = member(agentId);
        return member == null ? 1 : member.getWeight();
    }

    public int maxMemberWeight() {
        if (membersByAgentId == null || membersByAgentId.isEmpty()) {
            return 1;
        }
        return membersByAgentId.values().stream()
                .filter(member -> member != null)
                .mapToInt(member -> Math.max(1, member.getWeight()))
                .max()
                .orElse(1);
    }

    public int effectiveTaskCount(AgentCandidateScore score) {
        Object runtime = score == null || score.scoreBreakdown() == null ? null : score.scoreBreakdown().get("runtime");
        if (runtime instanceof Map<?, ?> map) {
            Number current = numberValue(map.get("currentTaskCount"));
            Number reserved = numberValue(map.get("reservedTaskCount"));
            return (current == null ? 0 : current.intValue()) + (reserved == null ? 0 : reserved.intValue());
        }
        return 0;
    }

    public int intBreakdown(AgentCandidateScore score, String key) {
        Number number = numberValue(score == null || score.scoreBreakdown() == null ? null : score.scoreBreakdown().get(key));
        return number == null ? 0 : number.intValue();
    }

    public Map<String, AgentPoolRoutingMember> normalizedMembers() {
        if (membersByAgentId == null || membersByAgentId.isEmpty()) {
            return Map.of();
        }
        Map<String, AgentPoolRoutingMember> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, AgentPoolRoutingMember> entry : membersByAgentId.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                normalized.put(normalizeAgentId(entry.getKey()), entry.getValue());
            }
        }
        return Map.copyOf(normalized);
    }

    static String normalizeAgentId(String agentId) {
        return agentId == null || agentId.isBlank()
                ? null
                : agentId.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private static Number numberValue(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
