package com.opensocket.aievent.core.dispatch.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FlowCapabilityShadowMigrationServiceTest {

    @Test
    void exactLegacyCandidateSetIsEquivalent() {
        var resolution = new FlowCapabilityShadowMigrationService.Resolution(List.of("manufacturing.work-order.trace"), List.of());
        var result = FlowCapabilityShadowMigrationService.compare(
                List.of("agent-a", "agent-b"), "agent-a", List.of("MES_WORK_ORDER_TRACE"), resolution,
                List.of("agent-a", "agent-b"));
        assertThat(result.result()).isEqualTo("EQUIVALENT_CANDIDATE_SET");
        assertThat(result.reasonCodes()).containsExactly("LEGACY_CANDIDATE_SET_PRESERVED");
    }

    @Test
    void expandingShadowSetPreservesLegacyWithoutBecomingAuthority() {
        var resolution = new FlowCapabilityShadowMigrationService.Resolution(List.of("manufacturing.work-order.trace"), List.of());
        var result = FlowCapabilityShadowMigrationService.compare(
                List.of("agent-a"), "agent-a", List.of("MES_WORK_ORDER_TRACE"), resolution,
                List.of("agent-a", "agent-b"));
        assertThat(result.result()).isEqualTo("SHADOW_EXPANDS_CANDIDATES");
        assertThat(result.reasonCodes()).contains("LEGACY_CANDIDATE_SET_PRESERVED");
    }

    @Test
    void legacySelectedAgentMissingFromCapabilitySetIsBlocker() {
        var resolution = new FlowCapabilityShadowMigrationService.Resolution(List.of("manufacturing.work-order.trace"), List.of());
        var result = FlowCapabilityShadowMigrationService.compare(
                List.of("agent-a", "agent-b"), "agent-b", List.of("MES_WORK_ORDER_TRACE"), resolution,
                List.of("agent-a"));
        assertThat(result.result()).isEqualTo("LEGACY_SELECTED_NOT_ELIGIBLE");
        assertThat(result.reasonCodes()).containsExactly("LEGACY_SELECTED_NOT_CAPABILITY_ELIGIBLE");
    }

    @Test
    void unresolvedSkillBlocksMigrationReadiness() {
        var resolution = new FlowCapabilityShadowMigrationService.Resolution(List.of(), List.of("UNKNOWN_SKILL"));
        var result = FlowCapabilityShadowMigrationService.compare(
                List.of("agent-a"), "agent-a", List.of("UNKNOWN_SKILL"), resolution, List.of());
        assertThat(result.result()).isEqualTo("UNMAPPED_CAPABILITY");
    }

    @Test
    void noRequiredCapabilityDoesNotInventSemanticContract() {
        var resolution = new FlowCapabilityShadowMigrationService.Resolution(List.of(), List.of());
        var result = FlowCapabilityShadowMigrationService.compare(
                List.of("agent-a"), "agent-a", List.of(), resolution, List.of("agent-a"));
        assertThat(result.result()).isEqualTo("NO_REQUIRED_CAPABILITY");
    }
}
