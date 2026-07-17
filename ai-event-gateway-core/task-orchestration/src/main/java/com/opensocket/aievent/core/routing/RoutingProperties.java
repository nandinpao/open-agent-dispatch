package com.opensocket.aievent.core.routing;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.opensocket.aievent.core.routing.cutover.DispatchCutoverMode;

@ConfigurationProperties(prefix = "routing")
public class RoutingProperties {
    private boolean assignmentEnabled = false;
    private int minimumScore = 50;
    private int maxCandidates = 20;
    private boolean updateTaskStatusOnAssignment = true;
    /** Lease TTL used by the single Stage 8 direct-dispatch assignment path. */
    private Duration assignmentLeaseTtl = Duration.ofMinutes(5);
    /**
     * P9.2 compatibility switch. When enabled, routing candidates are evaluated against the
     * Core Skill Registry in addition to legacy flat capability matching. The default remains
     * false so existing installations without migrated governance profiles do not suddenly lose
     * all candidate routing.
     */
    private boolean skillAwareEnabled = false;
    /**
     * When true, a known Skill Registry requirement that fails evaluation caps the candidate below
     * the default minimum score. When false, skill evaluation only adds diagnostics and positive
     * scoring hints.
     */
    private boolean skillAwareEnforced = true;

    /**
     * P3: keep agents that crossed the poison failure threshold out of new routing decisions even
     * after their short runtime backoff window has expired.
     */
    private boolean poisonAgentExclusionEnabled = true;
    private int poisonAgentFailureThreshold = 5;
    private boolean runtimeFailurePenaltyEnabled = true;
    private boolean loadAwareScoringEnabled = true;

    /** P5: enforce generic, data-driven routing for newly created work. */
    private boolean zeroSpecialCaseRuntimeEnabled = true;
    /** P5: allow retry/recovery of already-persisted legacy tasks using only their saved evidence. */
    private boolean persistedLegacyEvidenceRecoveryEnabled = true;

    /** P3: optional skill version compatibility hints, e.g. CAPABILITY_REVIEW@2 or SKILL_VERSION:CAPABILITY_REVIEW:2. */
    private boolean skillVersionCompatibilityEnabled = true;
    private boolean skillVersionEnforced = true;

    /**
     * P3-I/P3-O controlled rollout for Demand x Policy x Supply eligibility.
     * Supported values: LEGACY_ONLY, SHADOW, WARN, ENFORCE.
     * P3-O keeps dev/local on SHADOW, promotes prod default to WARN, and uses
     * application-enforce.yml for controlled ENFORCE cutover.
     */
    private String eligibilityEngineMode = EligibilityEngineMode.SHADOW.name();
    /** P3-O: ENFORCE routing decisions must retain V2 explainability in scoreBreakdown. */
    private boolean requireV2ScoreBreakdownInEnforce = true;
    /** P3-O: ENFORCE mode must not fall back to legacy Dispatch Flow Agent Assignment eligibility. */
    private boolean legacyProfileEligibilityDisabledInEnforce = true;

    /**
     * R6 controlled cutover: when enabled, tasks that carry matchedFlowId/matchedRuleId
     * and requestedSkill are treated as Flow -> Rule -> Skill -> Agent routing first.
     * Legacy fallback remains available until R9.
     */
    private boolean flowRuleRoutingEnabled = true;
    private boolean flowRuleLegacyFallbackEnabled = false;
    private boolean formalSuccessRequiresFlowRule = true;
    private boolean standaloneDispatchPolicyWritesEnabled = false;


    /** P11 authoritative dispatch. Non-authoritative states hold new work for operator review. */
    private boolean genericAuthoritativeEnabled = true;
    private String genericAuthoritativeDefaultMode = DispatchCutoverMode.AUTHORITATIVE.name();
    private int genericAuthoritativeDefaultCanaryPercentage = 100;
    private int genericAuthoritativeMinimumSampleSize = 50;
    private double genericAuthoritativeMaximumRequirementBlockedRate = 0.05d;
    private double genericAuthoritativeMaximumNoCandidateRate = 0.10d;
    private double genericAuthoritativeMaximumSelectionDifferenceRate = 0.20d;
    private boolean genericAuthoritativeAutoRollbackEnabled = true;


    public boolean isAssignmentEnabled() { return assignmentEnabled; }
    public void setAssignmentEnabled(boolean assignmentEnabled) { this.assignmentEnabled = assignmentEnabled; }
    public int getMinimumScore() { return minimumScore; }
    public void setMinimumScore(int minimumScore) { this.minimumScore = Math.max(0, Math.min(100, minimumScore)); }
    public int getMaxCandidates() { return maxCandidates; }
    public void setMaxCandidates(int maxCandidates) { this.maxCandidates = Math.max(1, Math.min(maxCandidates, 100)); }
    public boolean isUpdateTaskStatusOnAssignment() { return updateTaskStatusOnAssignment; }
    public void setUpdateTaskStatusOnAssignment(boolean updateTaskStatusOnAssignment) { this.updateTaskStatusOnAssignment = updateTaskStatusOnAssignment; }
    public Duration getAssignmentLeaseTtl() { return assignmentLeaseTtl; }
    public void setAssignmentLeaseTtl(Duration assignmentLeaseTtl) {
        this.assignmentLeaseTtl = assignmentLeaseTtl == null || assignmentLeaseTtl.isNegative() || assignmentLeaseTtl.isZero()
                ? Duration.ofMinutes(5)
                : assignmentLeaseTtl;
    }
    public boolean isSkillAwareEnabled() { return skillAwareEnabled; }
    public void setSkillAwareEnabled(boolean skillAwareEnabled) { this.skillAwareEnabled = skillAwareEnabled; }
    public boolean isSkillAwareEnforced() { return skillAwareEnforced; }
    public void setSkillAwareEnforced(boolean skillAwareEnforced) { this.skillAwareEnforced = skillAwareEnforced; }
    public boolean isPoisonAgentExclusionEnabled() { return poisonAgentExclusionEnabled; }
    public void setPoisonAgentExclusionEnabled(boolean poisonAgentExclusionEnabled) { this.poisonAgentExclusionEnabled = poisonAgentExclusionEnabled; }
    public int getPoisonAgentFailureThreshold() { return poisonAgentFailureThreshold; }
    public void setPoisonAgentFailureThreshold(int poisonAgentFailureThreshold) { this.poisonAgentFailureThreshold = Math.max(0, poisonAgentFailureThreshold); }
    public boolean isRuntimeFailurePenaltyEnabled() { return runtimeFailurePenaltyEnabled; }
    public void setRuntimeFailurePenaltyEnabled(boolean runtimeFailurePenaltyEnabled) { this.runtimeFailurePenaltyEnabled = runtimeFailurePenaltyEnabled; }
    public boolean isLoadAwareScoringEnabled() { return loadAwareScoringEnabled; }
    public void setLoadAwareScoringEnabled(boolean loadAwareScoringEnabled) { this.loadAwareScoringEnabled = loadAwareScoringEnabled; }
    public boolean isZeroSpecialCaseRuntimeEnabled() { return zeroSpecialCaseRuntimeEnabled; }
    public void setZeroSpecialCaseRuntimeEnabled(boolean value) { this.zeroSpecialCaseRuntimeEnabled = value; }
    public boolean isPersistedLegacyEvidenceRecoveryEnabled() { return persistedLegacyEvidenceRecoveryEnabled; }
    public void setPersistedLegacyEvidenceRecoveryEnabled(boolean value) { this.persistedLegacyEvidenceRecoveryEnabled = value; }
    public boolean isSkillVersionCompatibilityEnabled() { return skillVersionCompatibilityEnabled; }
    public void setSkillVersionCompatibilityEnabled(boolean skillVersionCompatibilityEnabled) { this.skillVersionCompatibilityEnabled = skillVersionCompatibilityEnabled; }
    public boolean isSkillVersionEnforced() { return skillVersionEnforced; }
    public void setSkillVersionEnforced(boolean skillVersionEnforced) { this.skillVersionEnforced = skillVersionEnforced; }
    public String getEligibilityEngineMode() { return eligibilityEngineMode; }
    public void setEligibilityEngineMode(String eligibilityEngineMode) { this.eligibilityEngineMode = blank(eligibilityEngineMode) ? EligibilityEngineMode.SHADOW.name() : eligibilityEngineMode; }
    public EligibilityEngineMode resolvedEligibilityEngineMode() { return EligibilityEngineMode.parse(eligibilityEngineMode); }
    public boolean isRequireV2ScoreBreakdownInEnforce() { return requireV2ScoreBreakdownInEnforce; }
    public void setRequireV2ScoreBreakdownInEnforce(boolean requireV2ScoreBreakdownInEnforce) { this.requireV2ScoreBreakdownInEnforce = requireV2ScoreBreakdownInEnforce; }
    public boolean isLegacyProfileEligibilityDisabledInEnforce() { return legacyProfileEligibilityDisabledInEnforce; }
    public void setLegacyProfileEligibilityDisabledInEnforce(boolean legacyProfileEligibilityDisabledInEnforce) { this.legacyProfileEligibilityDisabledInEnforce = legacyProfileEligibilityDisabledInEnforce; }

    public boolean isFlowRuleRoutingEnabled() { return flowRuleRoutingEnabled; }
    public void setFlowRuleRoutingEnabled(boolean flowRuleRoutingEnabled) { this.flowRuleRoutingEnabled = flowRuleRoutingEnabled; }
    public boolean isFlowRuleLegacyFallbackEnabled() { return flowRuleLegacyFallbackEnabled; }
    public void setFlowRuleLegacyFallbackEnabled(boolean flowRuleLegacyFallbackEnabled) { this.flowRuleLegacyFallbackEnabled = flowRuleLegacyFallbackEnabled; }
    public boolean isFormalSuccessRequiresFlowRule() { return formalSuccessRequiresFlowRule; }
    public void setFormalSuccessRequiresFlowRule(boolean formalSuccessRequiresFlowRule) { this.formalSuccessRequiresFlowRule = formalSuccessRequiresFlowRule; }
    public boolean isStandaloneDispatchPolicyWritesEnabled() { return standaloneDispatchPolicyWritesEnabled; }
    public void setStandaloneDispatchPolicyWritesEnabled(boolean standaloneDispatchPolicyWritesEnabled) { this.standaloneDispatchPolicyWritesEnabled = standaloneDispatchPolicyWritesEnabled; }
    public boolean isGenericAuthoritativeEnabled() { return genericAuthoritativeEnabled; }
    public void setGenericAuthoritativeEnabled(boolean value) { this.genericAuthoritativeEnabled = value; }
    public String getGenericAuthoritativeDefaultMode() { return genericAuthoritativeDefaultMode; }
    public void setGenericAuthoritativeDefaultMode(String value) { this.genericAuthoritativeDefaultMode = blank(value) ? DispatchCutoverMode.AUTHORITATIVE.name() : value; }
    public DispatchCutoverMode resolvedGenericAuthoritativeDefaultMode() {
        try { return DispatchCutoverMode.valueOf(genericAuthoritativeDefaultMode.trim().toUpperCase()); }
        catch (Exception ex) { return DispatchCutoverMode.AUTHORITATIVE; }
    }
    public int getGenericAuthoritativeDefaultCanaryPercentage() { return genericAuthoritativeDefaultCanaryPercentage; }
    public void setGenericAuthoritativeDefaultCanaryPercentage(int value) { this.genericAuthoritativeDefaultCanaryPercentage = Math.max(0, Math.min(100, value)); }
    public int getGenericAuthoritativeMinimumSampleSize() { return genericAuthoritativeMinimumSampleSize; }
    public void setGenericAuthoritativeMinimumSampleSize(int value) { this.genericAuthoritativeMinimumSampleSize = Math.max(1, value); }
    public double getGenericAuthoritativeMaximumRequirementBlockedRate() { return genericAuthoritativeMaximumRequirementBlockedRate; }
    public void setGenericAuthoritativeMaximumRequirementBlockedRate(double value) { this.genericAuthoritativeMaximumRequirementBlockedRate = boundedRate(value); }
    public double getGenericAuthoritativeMaximumNoCandidateRate() { return genericAuthoritativeMaximumNoCandidateRate; }
    public void setGenericAuthoritativeMaximumNoCandidateRate(double value) { this.genericAuthoritativeMaximumNoCandidateRate = boundedRate(value); }
    public double getGenericAuthoritativeMaximumSelectionDifferenceRate() { return genericAuthoritativeMaximumSelectionDifferenceRate; }
    public void setGenericAuthoritativeMaximumSelectionDifferenceRate(double value) { this.genericAuthoritativeMaximumSelectionDifferenceRate = boundedRate(value); }
    public boolean isGenericAuthoritativeAutoRollbackEnabled() { return genericAuthoritativeAutoRollbackEnabled; }
    public void setGenericAuthoritativeAutoRollbackEnabled(boolean value) { this.genericAuthoritativeAutoRollbackEnabled = value; }


    private static double boundedRate(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0d;
        return Math.max(0d, Math.min(1d, value));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
