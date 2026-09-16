package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 3 WHO MAY governance authority.
 *
 * <p>Authorization is a hard gate. This service never ranks candidates, never selects a
 * provider and never chooses a transport. The preview evaluator persists append-only
 * PREVIEW evidence only; Dispatch/A2A runtimes are intentionally not wired to it in Phase 3.</p>
 */
@Service
public class DelegationGovernanceService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final Set<String> EFFECTS = Set.of("ALLOW", "DENY");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "SUSPENDED", "RETIRED");
    private static final Set<String> PRINCIPAL_TYPES = Set.of("HUMAN", "AGENT", "SERVICE_ACCOUNT", "SYSTEM");
    private static final Set<String> ACCESS_MODES = Set.of("READ", "WRITE", "EXECUTE");
    private static final Set<String> APPROVAL_MODES = Set.of("NONE", "SINGLE", "DUAL");
    private static final Set<String> PROVIDER_TYPES = Set.of("MANAGED_AGENT", "REMOTE_A2A_AGENT", "MCP_TOOL", "INTERNAL_SERVICE");
    private static final List<String> SENSITIVITY_ORDER = List.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED", "CRITICAL");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public DelegationGovernanceService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<DelegationPolicy> listPolicies(String tenantId, String status, String effect, String capabilityCode,
                                               String search, String afterPolicyId, int limit) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenant).addValue("limit", normalizeLimit(limit));
        StringBuilder where = new StringBuilder(" where tenant_id=:tenantId");
        if (!blank(status)) { where.append(" and status=:status"); params.addValue("status", normalizeStatus(status)); }
        else where.append(" and status<>'RETIRED'");
        if (!blank(effect)) { where.append(" and effect=:effect"); params.addValue("effect", normalizeEffect(effect)); }
        if (!blank(capabilityCode)) {
            where.append(" and capability_codes_json @> cast(:capabilityCodeJson as jsonb)");
            params.addValue("capabilityCodeJson", writeJson(List.of(capabilityCode.trim().toLowerCase(Locale.ROOT))));
        }
        if (!blank(search)) {
            where.append(" and (lower(policy_id) like :search or lower(display_name) like :search or lower(coalesce(description,'')) like :search)");
            params.addValue("search", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (!blank(afterPolicyId)) { where.append(" and policy_id>:afterPolicyId"); params.addValue("afterPolicyId", afterPolicyId.trim()); }
        return List.copyOf(jdbc.query("""
                select tenant_id,policy_id,display_name,description,effect,status,
                       requester_principal_types_json,requester_department_ids_json,requester_group_ids_json,requester_role_codes_json,
                       capability_codes_json,operations_json,resource_constraints_json,allowed_data_classes_json,max_sensitivity_level,
                       allowed_access_modes_json,required_provider_types_json,required_provider_certifications_json,approval_mode,
                       max_estimated_cost,max_delegation_depth,max_agent_calls,max_execution_time_ms,priority,version,created_at,updated_at
                from delegation_policies
                """ + where + " order by policy_id asc limit :limit", params, new PolicyRowMapper()));
    }

    @Transactional(readOnly = true)
    public Optional<DelegationPolicy> findPolicy(String tenantId, String policyId) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select tenant_id,policy_id,display_name,description,effect,status,
                           requester_principal_types_json,requester_department_ids_json,requester_group_ids_json,requester_role_codes_json,
                           capability_codes_json,operations_json,resource_constraints_json,allowed_data_classes_json,max_sensitivity_level,
                           allowed_access_modes_json,required_provider_types_json,required_provider_certifications_json,approval_mode,
                           max_estimated_cost,max_delegation_depth,max_agent_calls,max_execution_time_ms,priority,version,created_at,updated_at
                    from delegation_policies where tenant_id=:tenantId and policy_id=:policyId
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("policyId", requireNonBlank(policyId, "policyId")), new PolicyRowMapper()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Transactional
    public DelegationPolicy upsertPolicy(String tenantId, String pathPolicyId, DelegationPolicy request, String reason) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        if (request == null) throw new IllegalArgumentException("Delegation Policy request body is required");
        String policyId = requireNonBlank(firstNonBlank(pathPolicyId, request.policyId()), "policyId");
        if (!blank(request.policyId()) && !policyId.equals(request.policyId().trim())) {
            throw new IllegalArgumentException("policyId in the request body must match the path");
        }
        DelegationPolicy existing = findPolicy(tenant, policyId).orElse(null);
        String displayName = requireNonBlank(request.displayName(), "displayName");
        String effect = normalizeEffect(request.effect());
        String status = normalizeStatus(request.status());
        List<String> principalTypes = normalizeSet(request.requesterPrincipalTypes(), PRINCIPAL_TYPES, "requesterPrincipalTypes");
        List<String> departmentIds = normalizeIdentifiers(request.requesterDepartmentIds());
        List<String> groupIds = normalizeIdentifiers(request.requesterGroupIds());
        List<String> roleCodes = normalizeIdentifiers(request.requesterRoleCodes());
        List<String> capabilityCodes = normalizeCapabilityCodes(request.capabilityCodes());
        if (capabilityCodes.isEmpty()) throw new IllegalArgumentException("At least one Canonical Capability is required; Phase 3 policies cannot authorize an unspecified target topology");
        validateCapabilitiesExist(tenant, capabilityCodes);
        List<String> operations = normalizeUpper(request.operations());
        Map<String, Object> resourceConstraints = request.resourceConstraints() == null ? Map.of() : Map.copyOf(request.resourceConstraints());
        List<String> allowedDataClasses = normalizeUpper(request.allowedDataClasses());
        String maxSensitivity = normalizeSensitivity(request.maxSensitivityLevel());
        List<String> accessModes = normalizeAccessModes(request.allowedAccessModes());
        List<String> providerTypes = normalizeSet(request.requiredProviderTypes(), PROVIDER_TYPES, "requiredProviderTypes");
        List<String> certifications = normalizeUpper(request.requiredProviderCertifications());
        String approvalMode = normalizeApprovalMode(request.approvalMode());
        int priority = request.priority() == null ? 100 : request.priority();
        if (priority < 0 || priority > 1_000_000) throw new IllegalArgumentException("priority must be between 0 and 1000000");
        validateNonNegative(request.maxEstimatedCost(), "maxEstimatedCost");
        validateNonNegative(request.maxDelegationDepth(), "maxDelegationDepth");
        validateNonNegative(request.maxAgentCalls(), "maxAgentCalls");
        validateNonNegative(request.maxExecutionTimeMs(), "maxExecutionTimeMs");
        if ("DENY".equals(effect)) {
            if (!"NONE".equals(approvalMode)) {
                throw new IllegalArgumentException("DENY policies cannot request approval; a matching DENY is always a hard FAIL");
            }
            if (!blank(request.maxSensitivityLevel()) && !"CRITICAL".equals(maxSensitivity)) {
                throw new IllegalArgumentException("DENY policies use scope selectors only; maxSensitivityLevel must be CRITICAL or omitted");
            }
            if (request.maxEstimatedCost() != null || request.maxDelegationDepth() != null
                    || request.maxAgentCalls() != null || request.maxExecutionTimeMs() != null) {
                throw new IllegalArgumentException("DENY policies cannot define ALLOW safety ceilings; remove cost/depth/call/time limits");
            }
            maxSensitivity = "CRITICAL";
        }
        if (existing != null && !samePolicy(existing, request) && blank(reason)) {
            throw new IllegalArgumentException("A change reason is required when modifying an existing Delegation Policy");
        }
        if (existing == null && "ACTIVE".equals(status) && blank(reason)) {
            throw new IllegalArgumentException("A change reason is required when creating an ACTIVE Delegation Policy");
        }
        int version = existing == null ? 1 : existing.version() + 1;
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime createdAt = existing == null ? now : existing.createdAt();

        jdbc.update("""
                insert into delegation_policies(
                  tenant_id,policy_id,display_name,description,effect,status,requester_principal_types_json,
                  requester_department_ids_json,requester_group_ids_json,requester_role_codes_json,capability_codes_json,
                  operations_json,resource_constraints_json,allowed_data_classes_json,max_sensitivity_level,allowed_access_modes_json,
                  required_provider_types_json,required_provider_certifications_json,approval_mode,max_estimated_cost,max_delegation_depth,
                  max_agent_calls,max_execution_time_ms,priority,version,created_at,updated_at)
                values(:tenantId,:policyId,:displayName,:description,:effect,:status,cast(:principalTypes as jsonb),cast(:departmentIds as jsonb),
                  cast(:groupIds as jsonb),cast(:roleCodes as jsonb),cast(:capabilityCodes as jsonb),cast(:operations as jsonb),
                  cast(:resourceConstraints as jsonb),cast(:dataClasses as jsonb),:maxSensitivity,cast(:accessModes as jsonb),
                  cast(:providerTypes as jsonb),cast(:certifications as jsonb),:approvalMode,:maxEstimatedCost,:maxDelegationDepth,
                  :maxAgentCalls,:maxExecutionTimeMs,:priority,:version,:createdAt,:updatedAt)
                on conflict(tenant_id,policy_id) do update set
                  display_name=excluded.display_name,description=excluded.description,effect=excluded.effect,status=excluded.status,
                  requester_principal_types_json=excluded.requester_principal_types_json,requester_department_ids_json=excluded.requester_department_ids_json,
                  requester_group_ids_json=excluded.requester_group_ids_json,requester_role_codes_json=excluded.requester_role_codes_json,
                  capability_codes_json=excluded.capability_codes_json,operations_json=excluded.operations_json,
                  resource_constraints_json=excluded.resource_constraints_json,allowed_data_classes_json=excluded.allowed_data_classes_json,
                  max_sensitivity_level=excluded.max_sensitivity_level,allowed_access_modes_json=excluded.allowed_access_modes_json,
                  required_provider_types_json=excluded.required_provider_types_json,
                  required_provider_certifications_json=excluded.required_provider_certifications_json,approval_mode=excluded.approval_mode,
                  max_estimated_cost=excluded.max_estimated_cost,max_delegation_depth=excluded.max_delegation_depth,
                  max_agent_calls=excluded.max_agent_calls,max_execution_time_ms=excluded.max_execution_time_ms,
                  priority=excluded.priority,version=excluded.version,updated_at=excluded.updated_at
                """, new MapSqlParameterSource("tenantId", tenant).addValue("policyId", policyId).addValue("displayName", displayName)
                .addValue("description", trimToNull(request.description())).addValue("effect", effect).addValue("status", status)
                .addValue("principalTypes", writeJson(principalTypes)).addValue("departmentIds", writeJson(departmentIds))
                .addValue("groupIds", writeJson(groupIds)).addValue("roleCodes", writeJson(roleCodes)).addValue("capabilityCodes", writeJson(capabilityCodes))
                .addValue("operations", writeJson(operations)).addValue("resourceConstraints", writeJson(resourceConstraints))
                .addValue("dataClasses", writeJson(allowedDataClasses)).addValue("maxSensitivity", maxSensitivity)
                .addValue("accessModes", writeJson(accessModes)).addValue("providerTypes", writeJson(providerTypes))
                .addValue("certifications", writeJson(certifications)).addValue("approvalMode", approvalMode)
                .addValue("maxEstimatedCost", request.maxEstimatedCost()).addValue("maxDelegationDepth", request.maxDelegationDepth())
                .addValue("maxAgentCalls", request.maxAgentCalls()).addValue("maxExecutionTimeMs", request.maxExecutionTimeMs())
                .addValue("priority", priority).addValue("version", version).addValue("createdAt", createdAt).addValue("updatedAt", now));
        String effectiveReason = firstNonBlank(reason, "Policy saved as " + status);
        appendPolicyVersionSnapshot(tenant, policyId, version, effectiveReason, now);
        appendAudit(tenant, policyId, version, existing == null ? "CREATE" : "UPDATE", effectiveReason, now);
        return findPolicy(tenant, policyId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<DelegationPolicyVersion> policyVersions(String tenantId, String policyId) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        return List.copyOf(jdbc.query("""
                select tenant_id,policy_id,version,snapshot_json,change_reason,actor_ref,created_at
                from delegation_policy_versions where tenant_id=:tenantId and policy_id=:policyId
                order by version desc
                """, new MapSqlParameterSource("tenantId", tenant).addValue("policyId", requireNonBlank(policyId, "policyId")),
                (rs, rowNum) -> new DelegationPolicyVersion(rs.getString("tenant_id"), rs.getString("policy_id"), rs.getInt("version"),
                        readMap(rs.getString("snapshot_json")), rs.getString("change_reason"), rs.getString("actor_ref"),
                        rs.getObject("created_at", OffsetDateTime.class))));
    }

    @Transactional(readOnly = true)
    public List<DelegationPolicyAuditEvent> auditEvents(String tenantId, String policyId) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        return List.copyOf(jdbc.query("""
                select tenant_id,event_id,policy_id,policy_version,action,reason,actor_ref,occurred_at
                from delegation_policy_audit_events where tenant_id=:tenantId and policy_id=:policyId
                order by occurred_at asc,event_id asc
                """, new MapSqlParameterSource("tenantId", tenant).addValue("policyId", requireNonBlank(policyId, "policyId")),
                (rs, rowNum) -> new DelegationPolicyAuditEvent(rs.getString("tenant_id"), rs.getString("event_id"), rs.getString("policy_id"),
                        rs.getInt("policy_version"), rs.getString("action"), rs.getString("reason"), rs.getString("actor_ref"),
                        rs.getObject("occurred_at", OffsetDateTime.class))));
    }

    /**
     * Admin governance preview. The decision is evidence only and is not wired into Dispatch/A2A execution in Phase 3.
     */
    @Transactional
    public DelegationAuthorizationDecision evaluatePreview(String tenantId, DelegationAuthorizationRequest request) {
        return evaluate(tenantId, request, "PREVIEW");
    }

    /** Phase 12 server-side WHO MAY runtime evaluation. Requester context must be resolved by trusted runtime code. */
    @Transactional
    public DelegationAuthorizationDecision evaluateRuntime(String tenantId, DelegationAuthorizationRequest request) {
        return evaluate(tenantId, request, "RUNTIME");
    }

    private DelegationAuthorizationDecision evaluate(String tenantId, DelegationAuthorizationRequest request, String decisionMode) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        if (request == null || request.requirement() == null) throw new IllegalArgumentException("CapabilityRequirement is required");
        String capabilityCode = requireNonBlank(request.requirement().capabilityCode(), "requirement.capabilityCode").toLowerCase(Locale.ROOT);
        String operation = normalizeSingleUpper(request.requirement().operation(), "requirement.operation");
        String bindingId = requireNonBlank(request.bindingId(), "bindingId");
        Candidate candidate = loadCandidate(tenant, bindingId);
        List<String> reasons = new ArrayList<>();
        if (!capabilityCode.equals(candidate.capabilityCode())) reasons.add("BINDING_CAPABILITY_MISMATCH");
        if (!candidate.supportedOperations().isEmpty() && !candidate.supportedOperations().contains(operation)) reasons.add("OPERATION_NOT_SUPPORTED_BY_BINDING");
        if (!"APPROVED".equals(candidate.trustStatus())) reasons.add("BINDING_CATALOG_TRUST_NOT_APPROVED");
        if (Set.of("DISABLED", "RETIRED").contains(candidate.catalogStatus())) reasons.add("PROVIDER_NOT_ACTIVE");
        else if (!"REGISTERED".equals(candidate.catalogStatus())) reasons.add("PROVIDER_NOT_REGISTERED");
        if (!reasons.isEmpty()) return persistDecision(tenant, request, candidate, decisionMode, "FAIL", null, "NONE", reasons, List.of());

        List<DelegationPolicy> active = listActivePoliciesForCapability(tenant, capabilityCode);
        List<DelegationPolicy> scopeMatches = active.stream().filter(policy -> scopeMatches(policy, request, candidate, capabilityCode, operation)).toList();
        List<String> considered = scopeMatches.stream().map(DelegationPolicy::policyId).toList();

        List<DelegationPolicy> denyMatches = scopeMatches.stream().filter(policy -> "DENY".equals(policy.effect())).toList();
        if (!denyMatches.isEmpty()) {
            reasons.add("DENY_POLICY_MATCHED");
            reasons.addAll(denyMatches.stream().map(policy -> "DENY:" + policy.policyId()).toList());
            return persistDecision(tenant, request, candidate, decisionMode, "FAIL", null, "NONE", reasons, considered);
        }

        List<DelegationPolicy> allowMatches = scopeMatches.stream().filter(policy -> "ALLOW".equals(policy.effect())).toList();
        if (allowMatches.isEmpty()) {
            return persistDecision(tenant, request, candidate, decisionMode, "FAIL", null, "NONE", List.of("NO_MATCHING_ALLOW_POLICY"), considered);
        }
        DelegationPolicy selected = selectUnambiguousPolicy(allowMatches);
        if (selected == null) {
            return persistDecision(tenant, request, candidate, decisionMode, "FAIL", null, "NONE", List.of("POLICY_AMBIGUOUS"), considered);
        }

        List<String> hardFailures = hardConstraintFailures(selected, request, candidate);
        if (!hardFailures.isEmpty()) {
            return persistDecision(tenant, request, candidate, decisionMode, "FAIL", selected, selected.approvalMode(), hardFailures, considered);
        }
        int requiredApprovals = "DUAL".equals(selected.approvalMode()) ? 2 : "SINGLE".equals(selected.approvalMode()) ? 1 : 0;
        int approvalCount = request.approvalCount() == null ? 0 : Math.max(0, request.approvalCount());
        if (approvalCount < requiredApprovals) {
            return persistDecision(tenant, request, candidate, decisionMode, "WAITING_APPROVAL", selected, selected.approvalMode(),
                    List.of("HUMAN_APPROVAL_REQUIRED", "APPROVAL_COUNT_" + approvalCount + "_OF_" + requiredApprovals), considered);
        }
        return persistDecision(tenant, request, candidate, decisionMode, "PASS", selected, selected.approvalMode(), List.of("AUTHORIZED"), considered);
    }

    private DelegationPolicy selectUnambiguousPolicy(List<DelegationPolicy> policies) {
        Comparator<DelegationPolicy> ordering = Comparator
                .comparingInt((DelegationPolicy p) -> p.priority() == null ? 100 : p.priority()).reversed()
                .thenComparing(Comparator.comparingInt(this::specificity).reversed());
        List<DelegationPolicy> sorted = policies.stream().sorted(ordering).toList();
        DelegationPolicy first = sorted.get(0);
        int priority = first.priority() == null ? 100 : first.priority();
        int specificity = specificity(first);
        long ties = sorted.stream().filter(policy -> (policy.priority() == null ? 100 : policy.priority()) == priority && specificity(policy) == specificity).count();
        return ties == 1 ? first : null;
    }

    private int specificity(DelegationPolicy p) {
        int score = 0;
        score += nonEmpty(p.requesterPrincipalTypes());
        score += nonEmpty(p.requesterDepartmentIds());
        score += nonEmpty(p.requesterGroupIds());
        score += nonEmpty(p.requesterRoleCodes());
        score += nonEmpty(p.capabilityCodes());
        score += nonEmpty(p.operations());
        score += p.resourceConstraints() == null ? 0 : p.resourceConstraints().size();
        score += nonEmpty(p.allowedDataClasses());
        score += nonEmpty(p.allowedAccessModes());
        score += nonEmpty(p.requiredProviderTypes());
        score += nonEmpty(p.requiredProviderCertifications());
        return score;
    }

    private boolean scopeMatches(DelegationPolicy policy, DelegationAuthorizationRequest request, Candidate candidate,
                                 String capabilityCode, String operation) {
        if (!containsOrAny(policy.capabilityCodes(), capabilityCode)) return false;
        if (!policy.operations().isEmpty() && !policy.operations().contains(operation)) return false;
        String principalType = normalizeSingleUpper(request.requesterPrincipalType(), "requesterPrincipalType");
        if (!policy.requesterPrincipalTypes().isEmpty() && !policy.requesterPrincipalTypes().contains(principalType)) return false;
        if (!policy.requesterDepartmentIds().isEmpty() && (blank(request.requesterDepartmentId()) || !policy.requesterDepartmentIds().contains(request.requesterDepartmentId().trim()))) return false;
        if (!policy.requesterGroupIds().isEmpty() && safeList(request.requesterGroupIds()).stream().noneMatch(policy.requesterGroupIds()::contains)) return false;
        if (!policy.requesterRoleCodes().isEmpty() && safeList(request.requesterRoleCodes()).stream().noneMatch(policy.requesterRoleCodes()::contains)) return false;
        String accessMode = normalizeAccessMode(request.accessMode());
        if (!policy.allowedAccessModes().isEmpty() && !policy.allowedAccessModes().contains(accessMode)) return false;
        if (!policy.allowedDataClasses().isEmpty()) {
            String dataClass = normalizeNullableUpper(firstNonBlank(request.requirement().dataClassification(), ""));
            if (blank(dataClass) || !policy.allowedDataClasses().contains(dataClass)) return false;
        }
        if (!policy.requiredProviderTypes().isEmpty() && !policy.requiredProviderTypes().contains(candidate.providerType())) return false;
        return resourceConstraintsMatch(policy.resourceConstraints(), request.requirement().resourceConstraints());
    }

    private List<String> hardConstraintFailures(DelegationPolicy policy, DelegationAuthorizationRequest request, Candidate candidate) {
        List<String> failures = new ArrayList<>();
        String sensitivity = normalizeSensitivity(firstNonBlank(request.sensitivityLevel(), request.requirement().dataClassification()));
        if (sensitivityRank(sensitivity) > sensitivityRank(policy.maxSensitivityLevel())) failures.add("SENSITIVITY_EXCEEDS_POLICY");
        if (!candidate.certifications().containsAll(policy.requiredProviderCertifications())) failures.add("PROVIDER_CERTIFICATION_REQUIRED");
        if (policy.maxEstimatedCost() != null && request.estimatedCost() == null) failures.add("ESTIMATED_COST_REQUIRED_BY_POLICY");
        else if (policy.maxEstimatedCost() != null && request.estimatedCost().compareTo(policy.maxEstimatedCost()) > 0) failures.add("MAX_ESTIMATED_COST_EXCEEDED");
        if (policy.maxDelegationDepth() != null && request.delegationDepth() == null) failures.add("DELEGATION_DEPTH_REQUIRED_BY_POLICY");
        else if (policy.maxDelegationDepth() != null && request.delegationDepth() > policy.maxDelegationDepth()) failures.add("MAX_DELEGATION_DEPTH_EXCEEDED");
        if (policy.maxAgentCalls() != null && request.agentCalls() == null) failures.add("AGENT_CALL_COUNT_REQUIRED_BY_POLICY");
        else if (policy.maxAgentCalls() != null && request.agentCalls() > policy.maxAgentCalls()) failures.add("MAX_AGENT_CALLS_EXCEEDED");
        if (policy.maxExecutionTimeMs() != null && request.executionTimeMs() == null) failures.add("EXECUTION_TIME_REQUIRED_BY_POLICY");
        else if (policy.maxExecutionTimeMs() != null && request.executionTimeMs() > policy.maxExecutionTimeMs()) failures.add("MAX_EXECUTION_TIME_EXCEEDED");
        return failures;
    }

    private boolean resourceConstraintsMatch(Map<String, Object> policy, Map<String, Object> actual) {
        if (policy == null || policy.isEmpty()) return true;
        Map<String, Object> request = actual == null ? Map.of() : actual;
        for (Map.Entry<String, Object> entry : policy.entrySet()) {
            Object actualValue = request.get(entry.getKey());
            Object allowed = entry.getValue();
            if (allowed instanceof Collection<?> values) {
                if (actualValue == null || values.stream().noneMatch(item -> java.util.Objects.equals(String.valueOf(item), String.valueOf(actualValue)))) return false;
            } else if (!java.util.Objects.equals(String.valueOf(allowed), String.valueOf(actualValue))) return false;
        }
        return true;
    }

    private DelegationAuthorizationDecision persistDecision(String tenant, DelegationAuthorizationRequest request, Candidate candidate,
                                                             String decisionMode, String result, DelegationPolicy policy, String approvalMode,
                                                             List<String> reasons, List<String> considered) {
        String decisionId = "delegation-decision-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String capabilityCode = request.requirement().capabilityCode().trim().toLowerCase(Locale.ROOT);
        String operation = request.requirement().operation().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        jdbc.update("""
                insert into delegation_authorization_decisions(
                  tenant_id,decision_id,decision_mode,result,capability_code,operation,binding_id,provider_id,provider_type,
                  selected_policy_id,selected_policy_version,approval_mode,reason_codes_json,considered_policy_ids_json,evaluated_at)
                values(:tenantId,:decisionId,:decisionMode,:result,:capabilityCode,:operation,:bindingId,:providerId,:providerType,
                  :policyId,:policyVersion,:approvalMode,cast(:reasonCodes as jsonb),cast(:consideredPolicies as jsonb),:evaluatedAt)
                """, new MapSqlParameterSource("tenantId", tenant).addValue("decisionId", decisionId).addValue("decisionMode", decisionMode).addValue("result", result)
                .addValue("capabilityCode", capabilityCode).addValue("operation", operation).addValue("bindingId", candidate.bindingId())
                .addValue("providerId", candidate.providerId()).addValue("providerType", candidate.providerType())
                .addValue("policyId", policy == null ? null : policy.policyId()).addValue("policyVersion", policy == null ? null : policy.version())
                .addValue("approvalMode", approvalMode).addValue("reasonCodes", writeJson(reasons)).addValue("consideredPolicies", writeJson(considered))
                .addValue("evaluatedAt", now));
        return new DelegationAuthorizationDecision(decisionId, tenant, result, capabilityCode, operation, candidate.bindingId(), candidate.providerId(),
                candidate.providerType(), policy == null ? null : policy.policyId(), policy == null ? null : policy.version(), approvalMode, reasons, considered, now);
    }

    /**
     * Authorization correctness must never depend on UI/page limits: omitting a later DENY would fail open.
     * This query therefore evaluates the complete ACTIVE policy set for the requested capability server-side.
     */
    private List<DelegationPolicy> listActivePoliciesForCapability(String tenant, String capabilityCode) {
        return List.copyOf(jdbc.query("""
                select tenant_id,policy_id,display_name,description,effect,status,
                       requester_principal_types_json,requester_department_ids_json,requester_group_ids_json,requester_role_codes_json,
                       capability_codes_json,operations_json,resource_constraints_json,allowed_data_classes_json,max_sensitivity_level,
                       allowed_access_modes_json,required_provider_types_json,required_provider_certifications_json,approval_mode,
                       max_estimated_cost,max_delegation_depth,max_agent_calls,max_execution_time_ms,priority,version,created_at,updated_at
                from delegation_policies
                where tenant_id=:tenantId and status='ACTIVE'
                  and capability_codes_json @> cast(:capabilityCodeJson as jsonb)
                order by policy_id asc
                """, new MapSqlParameterSource("tenantId", tenant)
                .addValue("capabilityCodeJson", writeJson(List.of(capabilityCode))), new PolicyRowMapper()));
    }

    private Candidate loadCandidate(String tenant, String bindingId) {
        try {
            return jdbc.queryForObject("""
                    select b.binding_id,b.capability_code,b.provider_id,b.supported_operations_json,b.trust_status,
                           p.provider_type,p.catalog_status,p.metadata_json
                    from capability_bindings b
                    join capability_providers p on p.tenant_id=b.tenant_id and p.provider_id=b.provider_id
                    where b.tenant_id=:tenantId and b.binding_id=:bindingId
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("bindingId", bindingId), (rs, rowNum) -> {
                Map<String, Object> metadata = readMap(rs.getString("metadata_json"));
                return new Candidate(rs.getString("binding_id"), rs.getString("capability_code"), rs.getString("provider_id"),
                        rs.getString("provider_type"), rs.getString("catalog_status"), readStringList(rs.getString("supported_operations_json")),
                        rs.getString("trust_status"), certifications(metadata));
            });
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Capability Binding does not exist: " + bindingId);
        }
    }

    private Set<String> certifications(Map<String, Object> metadata) {
        Object raw = metadata.get("certifications");
        if (!(raw instanceof Collection<?> values)) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        values.stream().filter(java.util.Objects::nonNull).map(String::valueOf).map(value -> value.trim().toUpperCase(Locale.ROOT)).filter(value -> !value.isBlank()).forEach(result::add);
        return Set.copyOf(result);
    }

    private void validateCapabilitiesExist(String tenant, List<String> capabilityCodes) {
        for (String code : capabilityCodes) {
            Integer count = jdbc.queryForObject("select count(*) from capability_definitions where tenant_id=:tenantId and capability_code=:code and status<>'RETIRED'",
                    new MapSqlParameterSource("tenantId", tenant).addValue("code", code), Integer.class);
            if (count == null || count == 0) throw new IllegalArgumentException("Canonical Capability does not exist: " + code);
        }
    }

    private void appendPolicyVersionSnapshot(String tenant, String policyId, int version, String reason, OffsetDateTime now) {
        jdbc.update("""
                insert into delegation_policy_versions(tenant_id,policy_id,version,snapshot_json,change_reason,actor_ref,created_at)
                select tenant_id,policy_id,:version,to_jsonb(delegation_policies),:reason,:actorRef,:createdAt
                from delegation_policies where tenant_id=:tenantId and policy_id=:policyId
                """, new MapSqlParameterSource("tenantId", tenant).addValue("policyId", policyId).addValue("version", version)
                .addValue("reason", requireNonBlank(reason, "reason")).addValue("actorRef", effectiveActorRef()).addValue("createdAt", now));
    }

    private void appendAudit(String tenant, String policyId, int version, String action, String reason, OffsetDateTime now) {
        jdbc.update("""
                insert into delegation_policy_audit_events(tenant_id,event_id,policy_id,policy_version,action,reason,actor_ref,occurred_at)
                values(:tenantId,:eventId,:policyId,:version,:action,:reason,:actorRef,:occurredAt)
                """, new MapSqlParameterSource("tenantId", tenant).addValue("eventId", "delegation-policy-audit-" + UUID.randomUUID())
                .addValue("policyId", policyId).addValue("version", version).addValue("action", action).addValue("reason", requireNonBlank(reason, "reason"))
                .addValue("actorRef", effectiveActorRef()).addValue("occurredAt", now));
    }

    private boolean samePolicy(DelegationPolicy existing, DelegationPolicy request) {
        return java.util.Objects.equals(existing.displayName(), request.displayName())
                && java.util.Objects.equals(existing.description(), trimToNull(request.description()))
                && java.util.Objects.equals(existing.effect(), normalizeEffect(request.effect()))
                && java.util.Objects.equals(existing.status(), normalizeStatus(request.status()))
                && java.util.Objects.equals(existing.requesterPrincipalTypes(), normalizeSet(request.requesterPrincipalTypes(), PRINCIPAL_TYPES, "requesterPrincipalTypes"))
                && java.util.Objects.equals(existing.requesterDepartmentIds(), normalizeIdentifiers(request.requesterDepartmentIds()))
                && java.util.Objects.equals(existing.requesterGroupIds(), normalizeIdentifiers(request.requesterGroupIds()))
                && java.util.Objects.equals(existing.requesterRoleCodes(), normalizeIdentifiers(request.requesterRoleCodes()))
                && java.util.Objects.equals(existing.capabilityCodes(), normalizeCapabilityCodes(request.capabilityCodes()))
                && java.util.Objects.equals(existing.operations(), normalizeUpper(request.operations()))
                && java.util.Objects.equals(existing.resourceConstraints(), request.resourceConstraints() == null ? Map.of() : request.resourceConstraints())
                && java.util.Objects.equals(existing.allowedDataClasses(), normalizeUpper(request.allowedDataClasses()))
                && java.util.Objects.equals(existing.maxSensitivityLevel(), normalizePolicyMaxSensitivity(request.effect(), request.maxSensitivityLevel()))
                && java.util.Objects.equals(existing.allowedAccessModes(), normalizeAccessModes(request.allowedAccessModes()))
                && java.util.Objects.equals(existing.requiredProviderTypes(), normalizeSet(request.requiredProviderTypes(), PROVIDER_TYPES, "requiredProviderTypes"))
                && java.util.Objects.equals(existing.requiredProviderCertifications(), normalizeUpper(request.requiredProviderCertifications()))
                && java.util.Objects.equals(existing.approvalMode(), normalizeApprovalMode(request.approvalMode()))
                && java.util.Objects.equals(existing.maxEstimatedCost(), request.maxEstimatedCost())
                && java.util.Objects.equals(existing.maxDelegationDepth(), request.maxDelegationDepth())
                && java.util.Objects.equals(existing.maxAgentCalls(), request.maxAgentCalls())
                && java.util.Objects.equals(existing.maxExecutionTimeMs(), request.maxExecutionTimeMs())
                && java.util.Objects.equals(existing.priority(), request.priority() == null ? 100 : request.priority());
    }

    private List<String> normalizeCapabilityCodes(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) values.stream().filter(value -> !blank(value)).map(value -> value.trim().toLowerCase(Locale.ROOT)).forEach(result::add);
        return List.copyOf(result);
    }

    private List<String> normalizeIdentifiers(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) values.stream().filter(value -> !blank(value)).map(String::trim).forEach(result::add);
        return List.copyOf(result);
    }

    private List<String> normalizeUpper(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) values.stream().filter(value -> !blank(value)).map(value -> value.trim().toUpperCase(Locale.ROOT).replace(' ', '_')).forEach(result::add);
        return List.copyOf(result);
    }

    private List<String> normalizeSet(List<String> values, Set<String> allowed, String field) {
        List<String> normalized = normalizeUpper(values);
        for (String value : normalized) if (!allowed.contains(value)) throw new IllegalArgumentException("Unsupported " + field + " value: " + value);
        return normalized;
    }

    private List<String> normalizeAccessModes(List<String> values) {
        List<String> modes = normalizeSet(values, ACCESS_MODES, "allowedAccessModes");
        return modes.isEmpty() ? List.of("READ") : modes;
    }

    private String normalizeEffect(String value) {
        String normalized = blank(value) ? "ALLOW" : value.trim().toUpperCase(Locale.ROOT);
        if (!EFFECTS.contains(normalized)) throw new IllegalArgumentException("Unsupported effect: " + normalized);
        return normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = blank(value) ? "DRAFT" : value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new IllegalArgumentException("Unsupported status: " + normalized);
        return normalized;
    }

    private String normalizeApprovalMode(String value) {
        String normalized = blank(value) ? "NONE" : value.trim().toUpperCase(Locale.ROOT);
        if (!APPROVAL_MODES.contains(normalized)) throw new IllegalArgumentException("Unsupported approvalMode: " + normalized);
        return normalized;
    }

    private String normalizeAccessMode(String value) {
        String normalized = blank(value) ? "READ" : value.trim().toUpperCase(Locale.ROOT);
        if (!ACCESS_MODES.contains(normalized)) throw new IllegalArgumentException("Unsupported accessMode: " + normalized);
        return normalized;
    }

    private String normalizePolicyMaxSensitivity(String effect, String value) {
        return "DENY".equals(normalizeEffect(effect)) ? "CRITICAL" : normalizeSensitivity(value);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String normalizeSensitivity(String value) {
        String normalized = blank(value) ? "INTERNAL" : value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!SENSITIVITY_ORDER.contains(normalized)) throw new IllegalArgumentException("Unsupported sensitivityLevel: " + normalized);
        return normalized;
    }

    private int sensitivityRank(String value) { return SENSITIVITY_ORDER.indexOf(normalizeSensitivity(value)); }
    private String normalizeSingleUpper(String value, String field) { return requireNonBlank(value, field).toUpperCase(Locale.ROOT).replace(' ', '_'); }
    private String normalizeNullableUpper(String value) { return blank(value) ? null : value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'); }
    private boolean containsOrAny(List<String> values, String actual) { return values.contains(actual); }
    private int nonEmpty(List<?> values) { return values == null || values.isEmpty() ? 0 : 1; }

    private void validateNonNegative(BigDecimal value, String field) { if (value != null && value.signum() < 0) throw new IllegalArgumentException(field + " must be >= 0"); }
    private void validateNonNegative(Integer value, String field) { if (value != null && value < 0) throw new IllegalArgumentException(field + " must be >= 0"); }
    private void validateNonNegative(Long value, String field) { if (value != null && value < 0) throw new IllegalArgumentException(field + " must be >= 0"); }

    private void bindDatabaseTenantContext(String tenantId) {
        String tenant = requireTenant(tenantId);
        IamTenantExecutionContext requestContext = IamTenantContextHolder.current().orElse(null);
        if (requestContext != null && !"INSTANCE".equalsIgnoreCase(requestContext.tenantId()) && !tenant.equals(requestContext.tenantId())) {
            throw new IllegalArgumentException("Tenant context mismatch for Delegation Governance persistence");
        }
        String actor = requestContext == null || blank(requestContext.actorId()) ? "delegation-governance" : requestContext.actorId();
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, actor);
    }

    private String effectiveActorRef() {
        IamTenantExecutionContext context = IamTenantContextHolder.current().orElse(null);
        return context == null || blank(context.actorId()) ? "delegation-governance" : context.actorId();
    }

    private String requireTenant(String value) { return requireNonBlank(value, "tenantId"); }
    private int normalizeLimit(int value) { return Math.max(1, Math.min(value <= 0 ? 100 : value, 500)); }
    private String requireNonBlank(String value, String field) { if (blank(value)) throw new IllegalArgumentException(field + " is required"); return value.trim(); }
    private String firstNonBlank(String first, String second) { return !blank(first) ? first : second; }
    private String trimToNull(String value) { return blank(value) ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception ex) { throw new IllegalArgumentException("Delegation Governance JSON cannot be serialized", ex); }
    }
    private List<String> readStringList(String value) {
        try { return blank(value) ? List.of() : json.readValue(value, STRING_LIST); }
        catch (Exception ex) { throw new IllegalStateException("Delegation Governance list cannot be read", ex); }
    }
    private Map<String, Object> readMap(String value) {
        try { return blank(value) ? Map.of() : json.readValue(value, OBJECT_MAP); }
        catch (Exception ex) { throw new IllegalStateException("Delegation Governance object cannot be read", ex); }
    }

    private final class PolicyRowMapper implements RowMapper<DelegationPolicy> {
        @Override public DelegationPolicy mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DelegationPolicy(rs.getString("tenant_id"), rs.getString("policy_id"), rs.getString("display_name"), rs.getString("description"),
                    rs.getString("effect"), rs.getString("status"), readStringList(rs.getString("requester_principal_types_json")),
                    readStringList(rs.getString("requester_department_ids_json")), readStringList(rs.getString("requester_group_ids_json")),
                    readStringList(rs.getString("requester_role_codes_json")), readStringList(rs.getString("capability_codes_json")),
                    readStringList(rs.getString("operations_json")), readMap(rs.getString("resource_constraints_json")),
                    readStringList(rs.getString("allowed_data_classes_json")), rs.getString("max_sensitivity_level"),
                    readStringList(rs.getString("allowed_access_modes_json")), readStringList(rs.getString("required_provider_types_json")),
                    readStringList(rs.getString("required_provider_certifications_json")), rs.getString("approval_mode"),
                    rs.getBigDecimal("max_estimated_cost"), (Integer) rs.getObject("max_delegation_depth"), (Integer) rs.getObject("max_agent_calls"),
                    (Long) rs.getObject("max_execution_time_ms"), rs.getInt("priority"), rs.getInt("version"),
                    rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
        }
    }

    private record Candidate(String bindingId, String capabilityCode, String providerId, String providerType, String catalogStatus,
                             List<String> supportedOperations, String trustStatus, Set<String> certifications) {}
}
