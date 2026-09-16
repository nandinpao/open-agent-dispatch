package com.opensocket.aievent.core.dispatch.flow;

import static com.opensocket.aievent.core.dispatch.flow.DispatchFlowNormalizationSupport.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.ObjectMapper;

import com.opensocket.aievent.core.api.StandardApiErrorCode;
import com.opensocket.aievent.core.api.StandardApiException;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.routing.governance.CapabilityRequirementMode;
import com.opensocket.aievent.core.routing.governance.CandidatePoolMode;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedResourceSql;

@Service
public class DispatchFlowManagementService {
    private static final Logger log = LoggerFactory.getLogger(DispatchFlowManagementService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final DispatchFlowJsonCodec json;
    private final AgentPoolManagementService agentPools;
    private final DispatchFlowQueryService queries;
    private final DispatchSourceOwnershipResolver sourceOwnership;
    private final DispatchFlowAggregatePolicy aggregatePolicy;

    @Autowired(required = false)
    private TaskRepository taskRepository;

    public DispatchFlowManagementService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.json = new DispatchFlowJsonCodec(objectMapper);
        this.sourceOwnership = new DispatchSourceOwnershipResolver(jdbc);
        this.aggregatePolicy = new DispatchFlowAggregatePolicy(jdbc, sourceOwnership);
        this.agentPools = new AgentPoolManagementService(jdbc, objectMapper, sourceOwnership);
        this.queries = new DispatchFlowQueryService(jdbc, objectMapper);
    }

    public List<AgentPoolView> listAgentPools(String tenantId, String sourceSystem) {
        return agentPools.listAgentPools(tenantId, sourceSystem);
    }

    public List<AgentPoolView> listAgentPools(String tenantId, String sourceSystem, ResourceListScopeQueryPlan scope) {
        return agentPools.listAgentPools(tenantId, sourceSystem, scope);
    }

    public Optional<AgentPoolView> findAgentPool(String tenantId, String poolId) {
        return agentPools.findAgentPool(tenantId, poolId);
    }

    @Transactional
    public AgentPoolView createOrUpdateAgentPool(AgentPoolView request) {
        DispatchFlowPersistenceTenantContext.bind(jdbc, request == null ? null : request.getTenantId(), "dispatch-flow-management");
        return agentPools.createOrUpdateAgentPool(request, taskRepository);
    }

    @Transactional
    public AgentPoolView createOrUpdateAgentPool(AgentPoolView request, Integer expectedVersion) {
        DispatchFlowPersistenceTenantContext.bind(jdbc, request == null ? null : request.getTenantId(), "dispatch-flow-management");
        return agentPools.createOrUpdateAgentPool(request, expectedVersion, taskRepository);
    }

    public List<String> crossScopeAgentIds(String tenantId, String sourceSystem, List<AgentPoolMemberView> members) {
        return agentPools.crossScopeAgentIds(tenantId, sourceSystem, members);
    }

    @Transactional
    public void retireAgentPool(String tenantId, String poolId) {
        DispatchFlowPersistenceTenantContext.bind(jdbc, tenantId, "dispatch-flow-management");
        agentPools.retireAgentPool(tenantId, poolId);
    }

    public List<DispatchFlowView> listFlows(String tenantId, String sourceSystem) {
        return queries.listFlows(tenantId, sourceSystem);
    }

    public List<DispatchFlowView> listFlows(String tenantId, String sourceSystem, ResourceListScopeQueryPlan scope) {
        return queries.listFlows(tenantId, sourceSystem, scope);
    }

    public List<DispatchFlowView> listFlowsForAgent(String tenantId, String agentId) {
        return queries.listFlowsForAgent(tenantId, agentId);
    }

    public List<DispatchFlowView> listFlowsForAgent(String tenantId, String agentId, ResourceListScopeQueryPlan scope) {
        return queries.listFlowsForAgent(tenantId, agentId, scope);
    }

    public List<DispatchFlowAgentOptionView> agentOptions(String tenantId) {
        return queries.agentOptions(tenantId);
    }

    public Optional<DispatchFlowView> findFlow(String tenantId, String flowId) {
        return queries.findFlow(tenantId, flowId);
    }

    public void requireAssignableSourceOwnership(ResourceListScopeQueryPlan plan, String tenantId, String resourceId, String sourceSystem) {
        sourceOwnership.requireAssignable(plan, tenantId, resourceId, sourceSystem);
    }

    public List<DispatchFlowRuleView> rules(String tenantId, String flowId) {
        return queries.rules(tenantId, flowId);
    }

    public List<Map<String, Object>> ruleConflicts(String tenantId, String flowId) {
        return queries.ruleConflicts(tenantId, flowId);
    }

    public List<DispatchFlowRequiredSkillView> skills(String tenantId, String flowId) {
        return queries.skills(tenantId, flowId);
    }

    public List<DispatchFlowAgentView> agents(String tenantId, String flowId) {
        return queries.agents(tenantId, flowId);
    }

    /**
     * Saves the complete Dispatch Flow aggregate in one transaction.
     *
     * <p>The request is authoritative for Flow-owned Rules. Required Capability rows and direct
     * Flow Agent selections are legacy compatibility references under the Agent Pool-first model;
     * standard UI requests marked with {@code legacyChildrenPreserved=true} retain existing legacy
     * child rows when they are not explicitly supplied. This prevents a normal Source Flow edit from
     * silently deleting historical Capability/Profile-era configuration.</p>
     */
    @Transactional
    public DispatchFlowView createOrUpdateFlow(DispatchFlowView request) {
        return createOrUpdateFlow(request, request == null ? null : request.getVersion());
    }

    @Transactional
    public DispatchFlowView createOrUpdateFlow(DispatchFlowView request, Integer expectedVersion) {
        DispatchFlowIssuePolicyEvidence.Snapshot issuePolicyInput = DispatchFlowIssuePolicyEvidence.capture(request);
        DispatchFlowPersistenceTenantContext.bind(jdbc, request == null ? null : request.getTenantId(), "dispatch-flow-management");
        DispatchFlowView normalized = aggregatePolicy.normalizeFlowAggregate(request);
        logIssuePolicyInputEvidence(issuePolicyInput, normalized);

        MapSqlParameterSource identity = params(normalized.getTenantId(), normalized.getFlowId());
        acquireAggregateLock(normalized.getTenantId(), normalized.getFlowId());
        DispatchFlowView existing = findFlow(normalized.getTenantId(), normalized.getFlowId()).orElse(null);
        assertExpectedVersion("Source Flow", normalized.getFlowId(), expectedVersion, existing == null ? null : existing.getVersion());
        preserveLegacyChildrenWhenRequested(normalized, existing);
        aggregatePolicy.validateAggregate(normalized);
        writeFlow(normalized, expectedVersion, existing);

        // Replace current Flow-owned rows after applying preserve-but-ignore semantics for legacy references.
        jdbc.update("delete from flow_agent_assignments where tenant_id = :tenantId and flow_id = :flowId", identity);
        jdbc.update("delete from flow_required_capabilities where tenant_id = :tenantId and flow_id = :flowId", identity);
        jdbc.update("delete from flow_rule_attribute_criteria where tenant_id = :tenantId and flow_id = :flowId", identity);
        jdbc.update("delete from dispatch_policies where tenant_id = :tenantId and flow_id = :flowId", identity);

        for (DispatchFlowRuleView rule : normalized.getRules()) {
            writeRule(rule);
        }
        for (DispatchFlowRequiredSkillView capability : normalized.getRequiredSkills()) {
            writeCapability(capability);
        }
        for (DispatchFlowAgentView agent : normalized.getAgents()) {
            writeAgent(agent);
        }

        DispatchFlowView saved = findFlow(normalized.getTenantId(), normalized.getFlowId())
                .orElseThrow(() -> new IllegalStateException("Dispatch Flow aggregate was not readable after save: " + normalized.getFlowId()));
        verifyPersistedAggregate(normalized, saved);
        registerIssuePolicyCommitEvidence(issuePolicyInput, normalized, existing, saved);
        int awakened = taskRepository == null ? 0 : taskRepository.wakeConfigurationBlockedTasks(
                normalized.getTenantId(), normalized.getSourceSystem(), OffsetDateTime.now(),
                "Dispatch Flow configuration changed: " + normalized.getFlowId());
        log.info("dispatch_flow_configuration_tasks_awakened tenantId={} flowId={} sourceSystem={} taskCount={}",
                normalized.getTenantId(), normalized.getFlowId(), normalized.getSourceSystem(), awakened);
        log.info("dispatch_flow_aggregate_saved tenantId={} flowId={} flowCode={} sourceSystem={} defaultIssueSyncPolicy={} ruleCount={} capabilityCount={} agentCount={} transactionMode=FLOW_RULE_REPLACEMENT_LEGACY_CHILDREN_PRESERVED",
                normalized.getTenantId(), normalized.getFlowId(), normalized.getFlowCode(), normalized.getSourceSystem(), normalized.getDefaultIssueSyncPolicy(),
                normalized.getRules().size(), normalized.getRequiredSkills().size(), normalized.getAgents().size());
        return saved;
    }


    private void logIssuePolicyInputEvidence(DispatchFlowIssuePolicyEvidence.Snapshot input, DispatchFlowView normalized) {
        boolean flowDefaultApplied = !input.flowPolicyProvided();
        var context = OpenDispatchRequestContextHolder.current().orElse(null);
        log.info("dispatch_flow_issue_policy_write_input tenantId={} flowId={} sourceSystem={} mutationSource={} requestFieldPresent={} requestValue={} modelValue={} normalizedValue={} serverDefaultApplied={} requestId={} correlationId={} operatorId={} requestKind={}",
                normalized.getTenantId(), normalized.getFlowId(), normalized.getSourceSystem(), input.mutationSource(),
                input.flowPolicyProvided(), DispatchFlowIssuePolicyEvidence.display(input.flowPolicyProvided(), input.flowPolicyValue()),
                input.flowPolicyValue() == null ? DispatchFlowIssuePolicyEvidence.NULL : input.flowPolicyValue(),
                normalized.getDefaultIssueSyncPolicy(), flowDefaultApplied,
                context == null ? null : context.requestId(), context == null ? null : context.correlationId(),
                context == null ? null : context.operatorId(), context == null ? null : context.requestKind());

        List<DispatchFlowRuleView> normalizedRules = normalized.getRules() == null ? List.of() : normalized.getRules();
        for (int index = 0; index < normalizedRules.size(); index++) {
            DispatchFlowRuleView rule = normalizedRules.get(index);
            DispatchFlowIssuePolicyEvidence.RuleInput raw = index < input.rules().size()
                    ? input.rules().get(index)
                    : new DispatchFlowIssuePolicyEvidence.RuleInput(index, rule.getRuleId(), rule.getRuleCode(), false, null);
            log.info("dispatch_rule_issue_policy_write_input tenantId={} flowId={} ruleId={} ruleCode={} mutationSource={} requestFieldPresent={} requestValue={} normalizedValue={} inheritanceFromFlow={} requestId={} correlationId={} operatorId={} requestKind={}",
                    normalized.getTenantId(), normalized.getFlowId(), rule.getRuleId(), rule.getRuleCode(), input.mutationSource(),
                    raw.policyProvided(), DispatchFlowIssuePolicyEvidence.display(raw.policyProvided(), raw.policyValue()),
                    rule.getIssueSyncPolicy() == null ? DispatchFlowIssuePolicyEvidence.NULL : rule.getIssueSyncPolicy(),
                    rule.getIssueSyncPolicy() == null,
                    context == null ? null : context.requestId(), context == null ? null : context.correlationId(),
                    context == null ? null : context.operatorId(), context == null ? null : context.requestKind());
        }
    }

    private void registerIssuePolicyCommitEvidence(
            DispatchFlowIssuePolicyEvidence.Snapshot input,
            DispatchFlowView normalized,
            DispatchFlowView existing,
            DispatchFlowView saved) {
        String operation = existing == null ? "CREATE" : "UPDATE";
        String tenantId = saved.getTenantId();
        String flowId = saved.getFlowId();
        String sourceSystem = saved.getSourceSystem();
        String mutationSource = input.mutationSource();
        var context = OpenDispatchRequestContextHolder.current().orElse(null);
        String requestId = context == null ? null : context.requestId();
        String correlationId = context == null ? null : context.correlationId();
        String operatorId = context == null ? null : context.operatorId();
        String requestKind = context == null ? null : context.requestKind();
        String previousFlowPolicy = existing == null ? null : existing.getDefaultIssueSyncPolicy();
        String persistedFlowPolicy = saved.getDefaultIssueSyncPolicy();
        boolean requestFieldPresent = input.flowPolicyProvided();
        String requestValue = DispatchFlowIssuePolicyEvidence.display(input.flowPolicyProvided(), input.flowPolicyValue());
        String modelValue = input.flowPolicyValue() == null ? DispatchFlowIssuePolicyEvidence.NULL : input.flowPolicyValue();
        boolean serverDefaultApplied = !input.flowPolicyProvided();
        boolean flowChanged = !Objects.equals(previousFlowPolicy, persistedFlowPolicy);

        Map<String, DispatchFlowRuleView> previousById = new LinkedHashMap<>();
        Map<String, DispatchFlowRuleView> previousByCode = new LinkedHashMap<>();
        if (existing != null && existing.getRules() != null) {
            for (DispatchFlowRuleView rule : existing.getRules()) {
                if (rule.getRuleId() != null) previousById.put(rule.getRuleId(), rule);
                if (rule.getRuleCode() != null) previousByCode.put(rule.getRuleCode(), rule);
            }
        }
        List<RuleCommitEvidence> rules = new ArrayList<>();
        List<DispatchFlowRuleView> persistedRules = saved.getRules() == null ? List.of() : saved.getRules();
        for (int index = 0; index < persistedRules.size(); index++) {
            DispatchFlowRuleView persisted = persistedRules.get(index);
            DispatchFlowIssuePolicyEvidence.RuleInput raw = index < input.rules().size()
                    ? input.rules().get(index)
                    : new DispatchFlowIssuePolicyEvidence.RuleInput(index, persisted.getRuleId(), persisted.getRuleCode(), false, null);
            DispatchFlowRuleView previous = previousById.get(persisted.getRuleId());
            if (previous == null) previous = previousByCode.get(persisted.getRuleCode());
            String previousPolicy = previous == null ? null : previous.getIssueSyncPolicy();
            rules.add(new RuleCommitEvidence(
                    persisted.getRuleId(), persisted.getRuleCode(), raw.policyProvided(),
                    DispatchFlowIssuePolicyEvidence.display(raw.policyProvided(), raw.policyValue()),
                    previousPolicy, persisted.getIssueSyncPolicy(), persisted.getIssueSyncPolicy() == null,
                    !Objects.equals(previousPolicy, persisted.getIssueSyncPolicy())));
        }

        persistIssuePolicyWriteAudit(
                tenantId, flowId, sourceSystem, operation, mutationSource, requestFieldPresent, requestValue, modelValue,
                previousFlowPolicy, persistedFlowPolicy, false, serverDefaultApplied, flowChanged,
                requestId, correlationId, operatorId, requestKind, null, null);
        for (RuleCommitEvidence rule : rules) {
            persistIssuePolicyWriteAudit(
                    tenantId, flowId, sourceSystem, operation, mutationSource, rule.requestFieldPresent(), rule.requestValue(),
                    rule.persistedValue(), rule.previousPersistedValue(), rule.persistedValue(), rule.inheritanceFromFlow(), false, rule.changed(),
                    requestId, correlationId, operatorId, requestKind, rule.ruleId(), rule.ruleCode());
        }

        Runnable committed = () -> {
            log.info("dispatch_flow_issue_policy_write_committed tenantId={} flowId={} sourceSystem={} operation={} mutationSource={} requestFieldPresent={} requestValue={} modelValue={} previousPersistedValue={} persistedValue={} changed={} serverDefaultApplied={} requestId={} correlationId={} operatorId={} requestKind={}",
                    tenantId, flowId, sourceSystem, operation, mutationSource, requestFieldPresent, requestValue, modelValue,
                    previousFlowPolicy == null ? DispatchFlowIssuePolicyEvidence.NULL : previousFlowPolicy, persistedFlowPolicy, flowChanged, serverDefaultApplied,
                    requestId, correlationId, operatorId, requestKind);
            for (RuleCommitEvidence rule : rules) {
                log.info("dispatch_rule_issue_policy_write_committed tenantId={} flowId={} ruleId={} ruleCode={} operation={} mutationSource={} requestFieldPresent={} requestValue={} previousPersistedValue={} persistedValue={} inheritanceFromFlow={} changed={} requestId={} correlationId={} operatorId={} requestKind={}",
                        tenantId, flowId, rule.ruleId(), rule.ruleCode(), operation, mutationSource, rule.requestFieldPresent(), rule.requestValue(),
                        rule.previousPersistedValue() == null ? DispatchFlowIssuePolicyEvidence.NULL : rule.previousPersistedValue(),
                        rule.persistedValue() == null ? DispatchFlowIssuePolicyEvidence.NULL : rule.persistedValue(),
                        rule.inheritanceFromFlow(), rule.changed(), requestId, correlationId, operatorId, requestKind);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committed.run();
                }
            });
        } else {
            // Defensive fallback for direct non-transactional test harnesses. Product mutations are transactional.
            committed.run();
        }
    }

    private void persistIssuePolicyWriteAudit(
            String tenantId, String flowId, String sourceSystem, String operation, String mutationSource,
            boolean requestFieldPresent, String requestValue, String modelValue, String previousPersistedValue,
            String persistedValue, boolean inheritanceFromFlow, boolean serverDefaultApplied, boolean changed,
            String requestId, String correlationId, String operatorId, String requestKind, String ruleId, String ruleCode) {
        jdbc.update("""
                insert into dispatch_issue_policy_write_audits(
                  tenant_id,audit_id,resource_type,flow_id,rule_id,rule_code,source_system,operation,mutation_source,
                  request_field_present,request_value,model_value,previous_persisted_value,persisted_value,
                  inheritance_from_flow,server_default_applied,changed,request_id,correlation_id,operator_id,request_kind,created_at)
                values(:tenantId,:auditId,:resourceType,:flowId,:ruleId,:ruleCode,:sourceSystem,:operation,:mutationSource,
                  :requestFieldPresent,:requestValue,:modelValue,:previousPersistedValue,:persistedValue,
                  :inheritanceFromFlow,:serverDefaultApplied,:changed,:requestId,:correlationId,:operatorId,:requestKind,now())
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("auditId", UUID.randomUUID())
                .addValue("resourceType", ruleId == null ? "FLOW" : "RULE")
                .addValue("flowId", flowId)
                .addValue("ruleId", ruleId)
                .addValue("ruleCode", ruleCode)
                .addValue("sourceSystem", sourceSystem)
                .addValue("operation", operation)
                .addValue("mutationSource", mutationSource == null ? "UNSPECIFIED" : mutationSource)
                .addValue("requestFieldPresent", requestFieldPresent)
                .addValue("requestValue", requestValue)
                .addValue("modelValue", modelValue)
                .addValue("previousPersistedValue", previousPersistedValue)
                .addValue("persistedValue", persistedValue)
                .addValue("inheritanceFromFlow", inheritanceFromFlow)
                .addValue("serverDefaultApplied", serverDefaultApplied)
                .addValue("changed", changed)
                .addValue("requestId", requestId)
                .addValue("correlationId", correlationId)
                .addValue("operatorId", operatorId)
                .addValue("requestKind", requestKind));
    }

    private record RuleCommitEvidence(
            String ruleId,
            String ruleCode,
            boolean requestFieldPresent,
            String requestValue,
            String previousPersistedValue,
            String persistedValue,
            boolean inheritanceFromFlow,
            boolean changed) {
    }

    private void assertExpectedVersion(String entityType, String entityId, Integer expectedVersion, Integer currentVersion) {
        if (currentVersion == null) {
            return;
        }
        if (expectedVersion == null) {
            throw new StandardApiException(StandardApiErrorCode.RESOURCE_VERSION_CONFLICT,
                    entityType + " requires expectedVersion / If-Match before update: " + entityId);
        }
        if (!expectedVersion.equals(currentVersion)) {
            throwVersionConflict(entityType, entityId, expectedVersion, currentVersion);
        }
    }

    private void throwVersionConflict(String entityType, String entityId, Integer expectedVersion, Integer currentVersion) {
        throw new StandardApiException(StandardApiErrorCode.RESOURCE_VERSION_CONFLICT,
                entityType + " was updated by another administrator. Reload before saving again. entityId=" + entityId
                        + ", expectedVersion=" + expectedVersion + ", currentVersion=" + currentVersion);
    }

    private void preserveLegacyChildrenWhenRequested(DispatchFlowView normalized, DispatchFlowView existing) {
        if (existing == null || !metadataFlag(normalized, "legacyChildrenPreserved")) {
            return;
        }
        if (normalized.getRequiredSkills().isEmpty() && !existing.getRequiredSkills().isEmpty()) {
            List<DispatchFlowRequiredSkillView> preservedCapabilities = new ArrayList<>();
            for (DispatchFlowRequiredSkillView existingCapability : existing.getRequiredSkills()) {
                preservedCapabilities.add(normalizeCapability(normalized.getTenantId(), normalized.getFlowId(), existingCapability));
            }
            normalized.setRequiredSkills(preservedCapabilities);
            log.info("dispatch_flow_legacy_required_capabilities_preserved tenantId={} flowId={} count={}",
                    normalized.getTenantId(), normalized.getFlowId(), preservedCapabilities.size());
        }
        if (normalized.getAgents().isEmpty() && !existing.getAgents().isEmpty()) {
            List<DispatchFlowAgentView> preservedAgents = new ArrayList<>();
            for (DispatchFlowAgentView existingAgent : existing.getAgents()) {
                preservedAgents.add(normalizeAgent(normalized.getTenantId(), normalized.getFlowId(), existingAgent));
            }
            normalized.setAgents(preservedAgents);
            log.info("dispatch_flow_legacy_agents_preserved tenantId={} flowId={} count={}",
                    normalized.getTenantId(), normalized.getFlowId(), preservedAgents.size());
        }
    }

    private boolean metadataFlag(DispatchFlowView flow, String key) {
        Object value = flow == null ? null : flow.getMetadata().get(key);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private void writeFlow(DispatchFlowView normalized, Integer expectedVersion, DispatchFlowView existing) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", normalized.getTenantId())
                .addValue("flowId", normalized.getFlowId())
                .addValue("flowCode", normalized.getFlowCode())
                .addValue("flowName", normalized.getFlowName())
                .addValue("sourceSystem", normalized.getSourceSystem())
                .addValue("ownerDepartmentId", normalized.getOwnerDepartmentId())
                .addValue("ownerGroupId", normalized.getOwnerGroupId())
                .addValue("flowType", firstNonBlank(normalized.getFlowType(), "SOURCE_FLOW"))
                .addValue("defaultPoolId", preserveNullableId(normalized.getDefaultPoolId()))
                .addValue("status", normalized.getStatus())
                .addValue("description", normalized.getDescription())
                .addValue("defaultCapabilityRequirementMode", normalized.getDefaultCapabilityRequirementMode())
                .addValue("defaultRequiredOperation", normalized.getDefaultRequiredOperation())
                .addValue("defaultSideEffectLevel", normalized.getDefaultSideEffectLevel())
                .addValue("defaultCandidatePoolMode", normalized.getDefaultCandidatePoolMode())
                .addValue("defaultRoutingStrategy", normalized.getDefaultRoutingStrategy())
                .addValue("defaultIssueSyncPolicy", normalized.getDefaultIssueSyncPolicy())
                .addValue("metadataJson", json.write(normalized.getMetadata()))
                .addValue("expectedVersion", expectedVersion);
        int flowRows = jdbc.update("""
                insert into dispatch_flows (
                    tenant_id, flow_id, flow_code, flow_name, source_system, owner_department_id, owner_group_id,
                    flow_type, default_pool_id, status, description, default_capability_requirement_mode,
                    default_required_operation, default_side_effect_level,
                    default_candidate_pool_mode, default_routing_strategy, issue_sync_policy, metadata_json, created_at, updated_at
                ) values (
                    :tenantId, :flowId, :flowCode, :flowName, :sourceSystem, :ownerDepartmentId, :ownerGroupId,
                    :flowType, :defaultPoolId, :status, :description, :defaultCapabilityRequirementMode,
                    :defaultRequiredOperation, :defaultSideEffectLevel,
                    :defaultCandidatePoolMode, :defaultRoutingStrategy, :defaultIssueSyncPolicy, cast(:metadataJson as jsonb), now(), now()
                )
                on conflict (tenant_id, flow_id) do update set
                    flow_code = excluded.flow_code,
                    flow_name = excluded.flow_name,
                    source_system = excluded.source_system,
                    owner_department_id = excluded.owner_department_id,
                    owner_group_id = excluded.owner_group_id,
                    flow_type = excluded.flow_type,
                    default_pool_id = excluded.default_pool_id,
                    status = excluded.status,
                    description = excluded.description,
                    default_capability_requirement_mode = excluded.default_capability_requirement_mode,
                    default_required_operation = excluded.default_required_operation,
                    default_side_effect_level = excluded.default_side_effect_level,
                    default_candidate_pool_mode = excluded.default_candidate_pool_mode,
                    default_routing_strategy = excluded.default_routing_strategy,
                    issue_sync_policy = excluded.issue_sync_policy,
                    metadata_json = excluded.metadata_json,
                    updated_at = now()
                where dispatch_flows.version = :expectedVersion
                """, params);
        if (existing != null && flowRows == 0) {
            throwVersionConflict("Source Flow", normalized.getFlowId(), expectedVersion, existing.getVersion());
        }
    }

    private DispatchFlowRuleView writeRule(DispatchFlowRuleView rule) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", rule.getTenantId())
                .addValue("ruleId", rule.getRuleId())
                .addValue("ruleCode", rule.getRuleCode())
                .addValue("ruleName", rule.getRuleName())
                .addValue("serviceCode", normalizeNullable(rule.getServiceCode()))
                .addValue("description", "Flow-owned Dispatch Rule: " + rule.getRuleName())
                .addValue("status", Boolean.TRUE.equals(rule.getEnabled()) ? "ACTIVE" : "DRAFT")
                .addValue("flowId", rule.getFlowId())
                .addValue("ruleScope", rule.getRuleScope())
                .addValue("eventStage", rule.getEventStage())
                .addValue("sourceSystem", rule.getSourceSystem())
                .addValue("originSourceSystem", normalizeNullable(rule.getOriginSourceSystem()))
                .addValue("targetSystem", normalizeNullable(rule.getTargetSystem()))
                .addValue("eventType", normalizeWildcard(rule.getEventType()))
                .addValue("objectType", normalizeWildcard(rule.getObjectType()))
                .addValue("errorCode", normalizeWildcard(rule.getErrorCode()))
                .addValue("conditionJson", json.write(rule.getCondition()))
                .addValue("priority", rule.getPriority() == null ? 100 : rule.getPriority())
                .addValue("matchMode", firstNonBlank(rule.getMatchMode(), "EXACT_OR_WILDCARD"))
                .addValue("targetPoolId", preserveNullableId(rule.getTargetPoolId()))
                .addValue("targetPoolCode", normalizeNullable(rule.getTargetPoolCode()))
                .addValue("requestedSkill", rule.getRequestedSkill())
                .addValue("capabilityRequirementMode", rule.getCapabilityRequirementMode())
                .addValue("requiredOperation", rule.getRequiredOperation())
                .addValue("sideEffectLevel", rule.getSideEffectLevel())
                .addValue("candidatePoolMode", rule.getCandidatePoolMode())
                .addValue("routingStrategy", rule.getRoutingStrategy())
                .addValue("explicitActionAuthorizationRequired", rule.getExplicitActionAuthorizationRequired())
                .addValue("requirementModelVersion", rule.getRequirementModelVersion())
                .addValue("handoffMode", normalizeNullable(rule.getHandoffMode()))
                .addValue("issuePolicyId", rule.getIssuePolicyId())
                .addValue("issueSyncPolicy", rule.getIssueSyncPolicy())
                .addValue("metadataJson", json.write(Map.of(
                        "configurationSource", "DB_BACKED_FLOW_RULE",
                        "routingModel", "AGENT_POOL_FIRST",
                        "priority", rule.getPriority() == null ? 100 : rule.getPriority())));
        jdbc.update("""
                insert into dispatch_policies (
                    tenant_id, policy_id, policy_code, policy_name, description,
                    risk_level, status, version, metadata_json, service_code,
                    flow_id, rule_scope, event_stage, source_system, origin_source_system,
                    target_system, event_type, object_type, error_code, condition_json,
                    priority, match_mode, target_pool_id, target_pool_code,
                    requested_skill, capability_requirement_mode, required_operation,
                    side_effect_level, candidate_pool_mode, routing_strategy,
                    explicit_action_authorization_required, requirement_model_version,
                    handoff_mode, issue_policy_id, issue_sync_policy,
                    created_at, updated_at
                ) values (
                    :tenantId, :ruleId, :ruleCode, :ruleName, :description,
                    'MIDDLE', :status, 1, cast(:metadataJson as jsonb), :serviceCode,
                    :flowId, :ruleScope, :eventStage, :sourceSystem, :originSourceSystem,
                    :targetSystem, :eventType, :objectType, :errorCode, cast(:conditionJson as jsonb),
                    :priority, :matchMode, :targetPoolId, :targetPoolCode,
                    :requestedSkill, :capabilityRequirementMode, :requiredOperation,
                    :sideEffectLevel, :candidatePoolMode, :routingStrategy,
                    :explicitActionAuthorizationRequired, :requirementModelVersion,
                    :handoffMode, :issuePolicyId, :issueSyncPolicy,
                    now(), now()
                )
                on conflict (policy_id) do update set
                    policy_code = excluded.policy_code,
                    policy_name = excluded.policy_name,
                    description = excluded.description,
                    status = excluded.status,
                    metadata_json = excluded.metadata_json,
                    service_code = excluded.service_code,
                    flow_id = excluded.flow_id,
                    rule_scope = excluded.rule_scope,
                    event_stage = excluded.event_stage,
                    source_system = excluded.source_system,
                    origin_source_system = excluded.origin_source_system,
                    target_system = excluded.target_system,
                    event_type = excluded.event_type,
                    object_type = excluded.object_type,
                    error_code = excluded.error_code,
                    condition_json = excluded.condition_json,
                    priority = excluded.priority,
                    match_mode = excluded.match_mode,
                    target_pool_id = excluded.target_pool_id,
                    target_pool_code = excluded.target_pool_code,
                    requested_skill = excluded.requested_skill,
                    capability_requirement_mode = excluded.capability_requirement_mode,
                    required_operation = excluded.required_operation,
                    side_effect_level = excluded.side_effect_level,
                    candidate_pool_mode = excluded.candidate_pool_mode,
                    routing_strategy = excluded.routing_strategy,
                    explicit_action_authorization_required = excluded.explicit_action_authorization_required,
                    requirement_model_version = excluded.requirement_model_version,
                    handoff_mode = excluded.handoff_mode,
                    issue_policy_id = excluded.issue_policy_id,
                    issue_sync_policy = excluded.issue_sync_policy,
                    updated_at = now()
                where dispatch_policies.tenant_id = excluded.tenant_id
                  and dispatch_policies.flow_id = excluded.flow_id
                """, params);
        syncRegisteredAttributeCriteria(rule);
        log.info("dispatch_flow_rule_upserted tenantId={} flowId={} ruleId={} ruleCode={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} requestedSkill={} registeredAttributeCriteria={} status={}",
                rule.getTenantId(), rule.getFlowId(), rule.getRuleId(), rule.getRuleCode(), rule.getSourceSystem(), rule.getEventStage(),
                rule.getObjectType(), rule.getEventType(), rule.getErrorCode(), rule.getRequestedSkill(), rule.getCondition() == null ? 0 : rule.getCondition().size(), Boolean.TRUE.equals(rule.getEnabled()) ? "ACTIVE" : "DRAFT");
        return rule;
    }

    private void syncRegisteredAttributeCriteria(DispatchFlowRuleView rule) {
        if (rule == null || rule.getCondition() == null || rule.getCondition().isEmpty()) return;
        String criterionStatus = Boolean.TRUE.equals(rule.getEnabled()) ? "ACTIVE" : "DISABLED";
        int sequence = 0;
        for (Map.Entry<String,Object> entry : rule.getCondition().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String attributeCode = normalizeCode(entry.getKey());
            if (blank(attributeCode)) continue;
            Integer registered = jdbc.queryForObject("""
                    select count(*) from flow_match_attribute_registry
                     where attribute_code=:attributeCode and status='ACTIVE' and authoritative=true and index_strategy<>'NONE'
                    """, new MapSqlParameterSource("attributeCode", attributeCode), Integer.class);
            if (registered == null || registered == 0) {
                log.warn("dispatch_flow_rule_condition_not_authoritative tenantId={} flowId={} ruleId={} conditionKey={} reason=ATTRIBUTE_NOT_REGISTERED_RAW_CONDITION_RETAINED_ONLY",
                        rule.getTenantId(), rule.getFlowId(), rule.getRuleId(), entry.getKey());
                continue;
            }
            String criterionId = rule.getRuleId() + ":attr:" + (++sequence) + ":" + attributeCode;
            MapSqlParameterSource criterion = new MapSqlParameterSource()
                    .addValue("tenantId", rule.getTenantId()).addValue("flowId", rule.getFlowId()).addValue("ruleId", rule.getRuleId())
                    .addValue("criterionId", criterionId).addValue("attributeCode", attributeCode).addValue("operator", "EQ")
                    .addValue("typedValue", json.write(entry.getValue())).addValue("status", criterionStatus);
            jdbc.update("""
                    insert into flow_rule_attribute_criteria(tenant_id,flow_id,rule_id,criterion_id,attribute_code,operator,typed_value_json,required,status,version,created_at,updated_at)
                    values(:tenantId,:flowId,:ruleId,:criterionId,:attributeCode,:operator,cast(:typedValue as jsonb),true,:status,1,now(),now())
                    on conflict(tenant_id,criterion_id) do update set attribute_code=excluded.attribute_code,operator=excluded.operator,
                      typed_value_json=excluded.typed_value_json,required=true,status=excluded.status,version=flow_rule_attribute_criteria.version+1,updated_at=now()
                    """, criterion);
        }
    }

    private DispatchFlowRequiredSkillView writeCapability(DispatchFlowRequiredSkillView capability) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", capability.getTenantId())
                .addValue("id", capability.getId())
                .addValue("flowId", capability.getFlowId())
                .addValue("ruleId", emptyToNull(capability.getRuleId()))
                .addValue("eventStage", capability.getEventStage())
                .addValue("agentRole", capability.getAgentRole())
                .addValue("capabilityCode", capability.getSkillCode())
                .addValue("capabilityName", capability.getSkillName())
                .addValue("capabilityKind", capability.getSkillKind())
                .addValue("authorityCode", capability.getAuthorityCode())
                .addValue("required", capability.getRequired())
                .addValue("openClawCapability", capability.getOpenClawCapability())
                .addValue("description", capability.getDescription())
                .addValue("metadataJson", json.write(Map.of("p1DbBackedCrud", true)));
        jdbc.update("""
                insert into flow_required_capabilities (
                    tenant_id, id, flow_id, rule_id, event_stage, agent_role,
                    skill_code, authority_code, required, metadata_json,
                    skill_name, skill_kind, openclaw_skill, description,
                    created_at, updated_at
                ) values (
                    :tenantId, :id, :flowId, :ruleId, :eventStage, :agentRole,
                    :capabilityCode, :authorityCode, :required, cast(:metadataJson as jsonb),
                    :capabilityName, :capabilityKind, :openClawCapability, :description,
                    now(), now()
                )
                on conflict (tenant_id, id) do update set
                    flow_id = excluded.flow_id,
                    rule_id = excluded.rule_id,
                    event_stage = excluded.event_stage,
                    agent_role = excluded.agent_role,
                    skill_code = excluded.skill_code,
                    authority_code = excluded.authority_code,
                    required = excluded.required,
                    metadata_json = excluded.metadata_json,
                    skill_name = excluded.skill_name,
                    skill_kind = excluded.skill_kind,
                    openclaw_skill = excluded.openclaw_skill,
                    description = excluded.description,
                    updated_at = now()
                where flow_required_capabilities.flow_id = excluded.flow_id
                """, params);
        log.info("dispatch_flow_capability_upserted tenantId={} flowId={} id={} eventStage={} capabilityCode={} required={}",
                capability.getTenantId(), capability.getFlowId(), capability.getId(), capability.getEventStage(), capability.getSkillCode(), capability.getRequired());
        return capability;
    }

    private DispatchFlowAgentView writeAgent(DispatchFlowAgentView agent) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", agent.getTenantId())
                .addValue("id", agent.getId())
                .addValue("flowId", agent.getFlowId())
                .addValue("agentId", agent.getAgentId())
                .addValue("agentName", agent.getAgentName())
                .addValue("eventStage", agent.getEventStage())
                .addValue("agentRole", agent.getAgentRole())
                .addValue("assignmentStatus", agent.getAssignmentStatus())
                .addValue("runtimeStatus", agent.getRuntimeStatus())
                .addValue("approvalStatus", agent.getApprovalStatus())
                .addValue("capabilityCoverageTotal", agent.getCapabilityCoverageTotal() == null ? 0 : agent.getCapabilityCoverageTotal())
                .addValue("capabilityCoverageMatched", agent.getCapabilityCoverageMatched() == null ? 0 : agent.getCapabilityCoverageMatched())
                .addValue("missingCapabilitysJson", json.write(agent.getMissingSkills()))
                .addValue("missingAuthoritiesJson", json.write(agent.getMissingAuthorities()))
                .addValue("readinessStatus", agent.getReadinessStatus())
                .addValue("metadataJson", json.write(Map.of("p1DbBackedCrud", true)));
        jdbc.update("""
                insert into flow_agent_assignments (
                    tenant_id, id, flow_id, agent_id, agent_name, event_stage, agent_role,
                    assignment_status, runtime_status, approval_status, skill_coverage_total,
                    skill_coverage_matched, missing_skills_json, missing_authorities_json,
                    readiness_status, metadata_json, created_at, updated_at
                ) values (
                    :tenantId, :id, :flowId, :agentId, :agentName, :eventStage, :agentRole,
                    :assignmentStatus, :runtimeStatus, :approvalStatus, :capabilityCoverageTotal,
                    :capabilityCoverageMatched, cast(:missingCapabilitysJson as jsonb), cast(:missingAuthoritiesJson as jsonb),
                    :readinessStatus, cast(:metadataJson as jsonb), now(), now()
                )
                on conflict (tenant_id, id) do update set
                    flow_id = excluded.flow_id,
                    agent_id = excluded.agent_id,
                    agent_name = excluded.agent_name,
                    event_stage = excluded.event_stage,
                    agent_role = excluded.agent_role,
                    assignment_status = excluded.assignment_status,
                    runtime_status = excluded.runtime_status,
                    approval_status = excluded.approval_status,
                    skill_coverage_total = excluded.skill_coverage_total,
                    skill_coverage_matched = excluded.skill_coverage_matched,
                    missing_skills_json = excluded.missing_skills_json,
                    missing_authorities_json = excluded.missing_authorities_json,
                    readiness_status = excluded.readiness_status,
                    metadata_json = excluded.metadata_json,
                    updated_at = now()
                where flow_agent_assignments.flow_id = excluded.flow_id
                """, params);
        log.info("dispatch_flow_agent_upserted tenantId={} flowId={} id={} agentId={} eventStage={} assignmentStatus={} approvalStatus={} readinessStatus={}",
                agent.getTenantId(), agent.getFlowId(), agent.getId(), agent.getAgentId(), agent.getEventStage(),
                agent.getAssignmentStatus(), agent.getApprovalStatus(), agent.getReadinessStatus());
        return agent;
    }

    private void acquireAggregateLock(String tenantId, String flowId) {
        jdbc.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(:aggregateKey, 0))",
                new MapSqlParameterSource().addValue("aggregateKey", tenantId + ":" + flowId));
    }

    private void verifyPersistedAggregate(DispatchFlowView expected, DispatchFlowView actual) {
        if (actual.getRules().size() != expected.getRules().size()
                || actual.getRequiredSkills().size() != expected.getRequiredSkills().size()
                || actual.getAgents().size() != expected.getAgents().size()) {
            throw new IllegalStateException("Persisted Dispatch Flow aggregate counts do not match the request.");
        }
    }

    @Transactional
    public void retireFlow(String tenantId, String flowId) {
        DispatchFlowPersistenceTenantContext.bind(jdbc, tenantId, "dispatch-flow-management");
        jdbc.update("""
                update dispatch_flows
                   set status = 'RETIRED', updated_at = now()
                 where tenant_id = :tenantId and flow_id = :flowId
                """, new MapSqlParameterSource()
                        .addValue("tenantId", normalizeTenant(tenantId))
                        .addValue("flowId", requireNonBlank(flowId, "flowId")));
        jdbc.update("""
                update dispatch_policies
                   set status = 'RETIRED', updated_at = now()
                 where tenant_id = :tenantId and flow_id = :flowId
                """, new MapSqlParameterSource()
                        .addValue("tenantId", normalizeTenant(tenantId))
                        .addValue("flowId", flowId));
    }

    private MapSqlParameterSource params(String tenantId, String flowId) {
        return new MapSqlParameterSource().addValue("tenantId", normalizeTenant(tenantId)).addValue("flowId", flowId);
    }
}
