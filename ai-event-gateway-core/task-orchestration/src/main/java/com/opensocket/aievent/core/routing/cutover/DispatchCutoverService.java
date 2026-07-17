package com.opensocket.aievent.core.routing.cutover;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.task.TaskRecord;

/** P10 tenant/Flow controlled generic-dispatch authority and automatic rollback. */
@Service
public class DispatchCutoverService {
    private static final Logger log = LoggerFactory.getLogger(DispatchCutoverService.class);
    private static final String ACTOR = "P10_GENERIC_DISPATCH_CUTOVER";

    private final DispatchCutoverRepository repository;
    private final RoutingProperties properties;

    public DispatchCutoverService(DispatchCutoverRepository repository, RoutingProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public DispatchCutoverDecision decide(TaskRecord task) {
        if (task == null) throw new IllegalArgumentException("task is required");
        String tenantId = require(task.getTenantId(), "tenantId");
        String flowId = require(task.getMatchedFlowId(), "matchedFlowId");
        DispatchCutoverPolicy policy = repository.findEffectivePolicy(tenantId, flowId)
                .orElseGet(() -> defaultPolicy(tenantId, flowId));
        if (policy.isAutoRollbackEnabled() && policy.active()
                && (policy.getMode() == DispatchCutoverMode.CANARY
                    || policy.getMode() == DispatchCutoverMode.AUTHORITATIVE)) {
            DispatchCutoverReadiness readiness = repository.readiness(tenantId, flowId);
            String rollbackReason = rollbackReason(policy, readiness);
            if (rollbackReason != null) {
                if (!repository.markRolledBack(tenantId, policy.getPolicyId(), rollbackReason, ACTOR)) {
                    throw new IllegalStateException("Automatic cutover rollback could not be persisted for policy " + policy.getPolicyId());
                }
                policy.setMode(DispatchCutoverMode.ROLLED_BACK);
                policy.setStatus(DispatchCutoverPolicyStatus.ROLLED_BACK);
                policy.setRolledBackAt(OffsetDateTime.now(ZoneOffset.UTC));
                policy.setRollbackReason(rollbackReason);
                log.error("generic_dispatch_cutover_auto_rollback tenantId={} flowId={} policyId={} reason={} sampleSize={}",
                        tenantId, flowId, policy.getPolicyId(), rollbackReason, readiness.sampleSize());
            }
        }
        int bucket = deterministicBucket(tenantId, flowId, task.getTaskId());
        boolean authoritative = policy.active() && switch (policy.getMode()) {
            case AUTHORITATIVE -> true;
            case CANARY -> bucket < policy.getCanaryPercentage();
            case SHADOW, ROLLED_BACK, DISABLED -> false;
        };
        DispatchCutoverDecision decision = new DispatchCutoverDecision();
        decision.setTenantId(tenantId);
        decision.setDecisionId("cutover-decision-" + UUID.randomUUID());
        decision.setTaskId(require(task.getTaskId(), "taskId"));
        decision.setFlowId(flowId);
        decision.setPolicyId(policy.getPolicyId());
        decision.setConfiguredMode(policy.getMode());
        decision.setAuthoritative(authoritative);
        decision.setDeterministicBucket(bucket);
        decision.setReasonCode(reason(policy, authoritative));
        decision.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        decision.validate();
        repository.appendDecision(decision);
        return decision;
    }

    public void recordOutcome(TaskRecord task, DispatchCutoverDecision decision,
                              boolean requirementBlocked, boolean noCandidate,
                              String selectedAgentId, String legacySelectedAgentId, String reasonCode) {
        if (task == null || decision == null) return;
        DispatchCutoverOutcome outcome = new DispatchCutoverOutcome();
        outcome.setTenantId(task.getTenantId());
        outcome.setOutcomeId("cutover-outcome-" + UUID.randomUUID());
        outcome.setTaskId(task.getTaskId());
        outcome.setFlowId(task.getMatchedFlowId());
        outcome.setPolicyId(decision.getPolicyId());
        outcome.setAuthoritative(decision.isAuthoritative());
        outcome.setRequirementBlocked(requirementBlocked);
        outcome.setNoCandidate(noCandidate);
        outcome.setSelectedAgentDifferent(!same(selectedAgentId, legacySelectedAgentId)
                && selectedAgentId != null && legacySelectedAgentId != null);
        outcome.setSelectedAgentId(selectedAgentId);
        outcome.setLegacySelectedAgentId(legacySelectedAgentId);
        outcome.setReasonCode(reasonCode);
        outcome.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        outcome.validate();
        repository.appendOutcome(outcome);
    }

    public List<DispatchCutoverPolicy> policies(String tenantId, int limit) {
        return repository.listPolicies(require(tenantId, "tenantId"), Math.max(1, Math.min(limit, 1000)));
    }
    public DispatchCutoverReadiness readiness(String tenantId, String flowId) {
        return repository.readiness(require(tenantId, "tenantId"), require(flowId, "flowId"));
    }
    public DispatchCutoverPolicy savePolicy(String tenantId, String policyId, DispatchCutoverPolicy request, String actor) {
        require(actor, "actor");
        DispatchCutoverPolicy policy = request == null ? new DispatchCutoverPolicy() : request;
        policy.setTenantId(require(tenantId, "tenantId"));
        policy.setPolicyId(require(policyId, "policyId"));
        policy.setFlowId(normalizedFlow(policy.getFlowId()));
        if (policy.getStatus() == DispatchCutoverPolicyStatus.ROLLED_BACK
                || policy.getMode() == DispatchCutoverMode.ROLLED_BACK) {
            throw new IllegalArgumentException("Use rollback API to mark a policy rolled back");
        }
        policy.setRolledBackAt(null);
        policy.setRollbackReason(null);
        policy.setUpdatedBy(actor);
        policy.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (policy.getCreatedAt() == null) policy.setCreatedAt(policy.getUpdatedAt());
        if (policy.getCreatedBy() == null || policy.getCreatedBy().isBlank()) policy.setCreatedBy(actor);
        policy.validate();
        return repository.savePolicy(policy);
    }
    public DispatchCutoverPolicy rollback(String tenantId, String policyId, String reason, String actor) {
        require(reason, "reason"); require(actor, "actor");
        if (!repository.markRolledBack(require(tenantId,"tenantId"), require(policyId,"policyId"), reason, actor)) {
            throw new IllegalArgumentException("Active cutover policy not found: " + policyId);
        }
        return repository.findPolicy(tenantId, policyId).orElseThrow();
    }

    private DispatchCutoverPolicy defaultPolicy(String tenantId, String flowId) {
        DispatchCutoverPolicy policy = new DispatchCutoverPolicy();
        policy.setTenantId(tenantId);
        policy.setPolicyId("runtime-default:" + tenantId + ":" + flowId);
        policy.setFlowId(flowId);
        policy.setMode(properties.resolvedGenericAuthoritativeDefaultMode());
        policy.setCanaryPercentage(properties.getGenericAuthoritativeDefaultCanaryPercentage());
        policy.setMinimumSampleSize(properties.getGenericAuthoritativeMinimumSampleSize());
        policy.setMaximumRequirementBlockedRate(properties.getGenericAuthoritativeMaximumRequirementBlockedRate());
        policy.setMaximumNoCandidateRate(properties.getGenericAuthoritativeMaximumNoCandidateRate());
        policy.setMaximumSelectionDifferenceRate(properties.getGenericAuthoritativeMaximumSelectionDifferenceRate());
        policy.setAutoRollbackEnabled(properties.isGenericAuthoritativeAutoRollbackEnabled());
        policy.setStatus(properties.isGenericAuthoritativeEnabled()
                ? DispatchCutoverPolicyStatus.ACTIVE : DispatchCutoverPolicyStatus.SUSPENDED);
        policy.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        policy.setCreatedBy(ACTOR);
        policy.setUpdatedAt(policy.getCreatedAt());
        policy.setUpdatedBy(ACTOR);
        return policy;
    }

    private String rollbackReason(DispatchCutoverPolicy policy, DispatchCutoverReadiness r) {
        if (r == null || !r.authoritativeMetricsAvailable()
                || r.authoritativeSampleSize() < policy.getMinimumSampleSize()) return null;
        if (r.requirementBlockedRate() > policy.getMaximumRequirementBlockedRate()) return "REQUIREMENT_BLOCKED_RATE_EXCEEDED";
        if (r.noCandidateRate() > policy.getMaximumNoCandidateRate()) return "NO_CANDIDATE_RATE_EXCEEDED";
        if (policy.getMode() == DispatchCutoverMode.CANARY
                && r.controlSampleSize() >= policy.getMinimumSampleSize()
                && r.selectionDifferenceRate() > policy.getMaximumSelectionDifferenceRate()) {
            return "SELECTION_DIFFERENCE_RATE_EXCEEDED";
        }
        return null;
    }
    private static String reason(DispatchCutoverPolicy policy, boolean authoritative) {
        if (policy.getMode() == DispatchCutoverMode.ROLLED_BACK || policy.getStatus() == DispatchCutoverPolicyStatus.ROLLED_BACK) return "CUTOVER_ROLLED_BACK";
        if (!policy.active()) return "CUTOVER_POLICY_NOT_ACTIVE";
        if (authoritative) return policy.getMode() == DispatchCutoverMode.CANARY ? "CANARY_BUCKET_SELECTED" : "AUTHORITATIVE_MODE";
        return policy.getMode() == DispatchCutoverMode.CANARY ? "CANARY_CONTROL_BUCKET" : "SHADOW_MODE";
    }
    private static int deterministicBucket(String tenantId, String flowId, String taskId) {
        CRC32 crc = new CRC32();
        crc.update((tenantId + "|" + flowId + "|" + taskId).getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % 100L);
    }
    private static String normalizedFlow(String value) { return value == null || value.isBlank() ? "*" : value.trim(); }
    private static boolean same(String a, String b) { return normalize(a).equals(normalize(b)); }
    private static String normalize(String v) { return v == null ? "" : v.trim().toUpperCase(Locale.ROOT); }
    private static String require(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v;}
}
