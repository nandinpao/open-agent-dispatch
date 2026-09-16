package com.opensocket.aievent.core.capability;

/**
 * Phase 7 Planner SPI. A Planner may only propose Canonical Capability steps and dependencies.
 * It must not receive or return Provider IDs, Agent IDs, Agent Pools, target Domains, A2A/MCP/Netty adapters or credentials.
 */
public interface PlannerAgentPort {
    ExecutionPlanProposal propose(ExecutionPlanRequest request);
}
