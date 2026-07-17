package com.opensocket.aievent.core.agent;

public record CapacityReservationResult(
        boolean reserved,
        String agentId,
        int currentTaskCount,
        int reservedTaskCount,
        int maxConcurrentTasks,
        String reason
) {
    public static CapacityReservationResult rejected(String agentId, String reason) {
        return new CapacityReservationResult(false, agentId, 0, 0, 0, reason);
    }

    public static CapacityReservationResult reserved(AgentSnapshot agent) {
        return new CapacityReservationResult(true, agent.getAgentId(), agent.getCurrentTaskCount(),
                agent.getReservedTaskCount(), agent.getMaxConcurrentTasks(), "Agent capacity reserved");
    }
}
