package com.opensocket.aievent.core.agent.assignment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Contract-only advanced selection strategies; never registered in Current routing. */
final class AgentAdvancedSelectionStrategyCatalog {
    private AgentAdvancedSelectionStrategyCatalog() {
    }

    static List<AgentAdvancedSelectionStrategyContract> contracts() {
        return List.of(
                strategy("ROUND_ROBIN", "Round-robin（需 per-pool cursor）",
                        "Select the next eligible Pool member by an atomic per-pool cursor.",
                        "agent_pool_selection_cursor with tenant_id + pool_id + cursor_version; updates must use compare-and-swap or database row lock.",
                        "Multi-node dispatch must atomically advance one cursor per Agent Pool and skip unavailable members without double assignment.",
                        List.of("PER_POOL_CURSOR", "DATABASE_ATOMIC_UPDATE", "MULTI_NODE_CONCURRENCY_TEST", "SIMULATION_CURSOR_PREVIEW", "ASSIGNMENT_EVIDENCE_CURSOR"), List.of()),
                strategy("LOCAL_FIRST", "Local-first（需 locality 定義）",
                        "Prefer eligible Agents in the same locality tier before falling back to LOWEST_LOAD.",
                        "No mutable strategy state, but every candidate must carry normalized locality metadata.",
                        "Locality precedence must be deterministic: site > plant > region > gatewayNode > networkZone. Missing locality falls back to LOWEST_LOAD.",
                        List.of("LOCALITY_DIMENSIONS_DEFINED", "LOCALITY_NORMALIZATION", "FALLBACK_LOWEST_LOAD", "SIMULATION_LOCALITY_EXPLANATION", "ASSIGNMENT_EVIDENCE_LOCALITY"),
                        List.of("site", "plant", "region", "gatewayNode", "networkZone")),
                strategy("QUALITY_SCORE", "Quality score（觀察期後才能啟用）",
                        "Rank eligible Agents by an explicit quality formula after a minimum observation window and sample count.",
                        "Quality observations remain in agent_quality_metrics_window; strategy must snapshot the values used for each assignment.",
                        "Quality data must be immutable for the decision window and must distinguish Agent responsibility from upstream payload or configuration failures.",
                        List.of("MINIMUM_SAMPLE", "DECAY_WINDOW", "RESPONSIBILITY_SCOPE", "OBSERVATION_TO_ENFORCEMENT_APPROVAL", "SIMULATION_QUALITY_BREAKDOWN", "ASSIGNMENT_EVIDENCE_QUALITY"), List.of()),
                strategy("COST_AWARE", "Cost-aware（需成本主檔）",
                        "Rank eligible Agents by a declared cost formula and SLA-safe fallback.",
                        "Requires a tenant-scoped cost master; metadata-only cost is not accepted.",
                        "Concurrent assignments must reserve cost/capacity snapshots before selection evidence is emitted.",
                        List.of("COST_MASTER", "COST_FORMULA", "SLA_SAFE_FALLBACK", "SIMULATION_COST_BREAKDOWN", "ASSIGNMENT_EVIDENCE_COST"), List.of()),
                strategy("SLA_AWARE", "SLA-aware（需 SLA 主檔）",
                        "Rank eligible Agents by SLA deadline fit, latency envelope and fallback risk.",
                        "Requires a tenant-scoped SLA master and per-task SLA snapshot.",
                        "SLA estimates must be deterministic for the same task and candidate snapshots; fallback is LOWEST_LOAD when SLA metadata is incomplete.",
                        List.of("SLA_MASTER", "SLA_DEADLINE_SNAPSHOT", "LATENCY_ENVELOPE", "SIMULATION_SLA_BREAKDOWN", "ASSIGNMENT_EVIDENCE_SLA"), List.of()));
    }

    private static AgentAdvancedSelectionStrategyContract strategy(
            String strategyCode,
            String displayName,
            String formula,
            String stateStorage,
            String concurrencyDefinition,
            List<String> checks,
            List<String> localityDimensions) {
        AgentAdvancedSelectionStrategyContract contract = new AgentAdvancedSelectionStrategyContract();
        contract.setStrategyCode(strategyCode);
        contract.setDisplayName(displayName);
        contract.setStatus("CONTRACT_ONLY");
        contract.setProductionEnabled(false);
        contract.setSimulationRequired(true);
        contract.setSimulationSupportStatus("CONTRACT_REQUIRED_NOT_ENABLED");
        contract.setFormula(formula);
        contract.setStateStorage(stateStorage);
        contract.setConcurrencyDefinition(concurrencyDefinition);
        contract.setFallbackStrategy("LOWEST_LOAD");
        contract.setAssignmentEvidenceContract("Assignment Evidence must record strategyCode, formulaVersion, candidateSnapshot, selectedAgentId, fallbackApplied and strategy-specific breakdown.");
        contract.setUiExplanation(displayName + " is a Phase 9E contract-only advanced strategy. It is not selectable in Current routing until all readiness checks are complete.");
        contract.setRequiredReadinessChecks(checks);
        contract.setLocalityDimensions(localityDimensions);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("phase9eAdvancedSelectionStrategy", true);
        metadata.put("contractOnly", true);
        metadata.put("routingImpact", "NONE");
        metadata.put("notRegisteredInCurrentSelectionStrategyRegistry", true);
        metadata.put("qualityObservationNotDirectlyPromoted", "QUALITY_SCORE requires explicit approval and formula before using Phase 9B observations.");
        contract.setMetadata(metadata);
        return contract;
    }
}
