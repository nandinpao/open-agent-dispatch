package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityItem;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentCapability;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentRiskStatus;

@Service
public class AgentSkillRegistryService {
    public static final String TAXONOMY_VERSION = "opensocket-capability-taxonomy/v1";

    private final AgentSkillRegistryRepository repository;

    public AgentSkillRegistryService(AgentSkillRegistryRepository repository) {
        this.repository = repository;
    }

    /** Compatibility constructor for focused unit tests. */
    public AgentSkillRegistryService() {
        this(new InMemoryAgentSkillRegistryRepository());
    }

    public List<AgentSkillDefinition> search(String domain, boolean enabledOnly) {
        return repository.search(normalize(domain), enabledOnly);
    }

    public AgentSkillDefinition get(String skillCode) {
        return repository.findByCode(normalize(skillCode))
                .orElseThrow(() -> new IllegalArgumentException("Skill definition not found: " + skillCode));
    }

    @Transactional
    public AgentSkillDefinition upsert(AgentSkillDefinition input) {
        if (input == null || blank(input.getSkillCode())) {
            throw new IllegalArgumentException("skillCode is required");
        }
        AgentSkillDefinition normalized = normalizeDefinition(input);
        return repository.upsert(normalized);
    }

    @Transactional
    public boolean delete(String skillCode) {
        if (blank(skillCode)) {
            throw new IllegalArgumentException("skillCode is required");
        }
        return repository.delete(normalize(skillCode));
    }

    public String mode() {
        return repository.mode();
    }

    public List<AgentSkillVersion> listVersions(String skillCode) {
        return repository.listVersions(normalize(skillCode));
    }

    public List<AgentSkillAuditEntry> listAuditEntries(String skillCode, int limit) {
        return repository.listAuditEntries(normalize(skillCode), limit);
    }

    public AgentSkillApprovalPolicy getApprovalPolicy(String skillCode) {
        return repository.findApprovalPolicy(normalize(skillCode)).orElseGet(() -> defaultApprovalPolicy(skillCode));
    }

    @Transactional
    public AgentSkillApprovalPolicy updateApprovalPolicy(String skillCode, AgentSkillApprovalPolicy request, String operatorId) {
        AgentSkillApprovalPolicy policy = request == null ? new AgentSkillApprovalPolicy() : request;
        policy.setSkillCode(normalize(skillCode));
        policy.setSubmitRoles(normalizeList(policy.getSubmitRoles()));
        policy.setApproveRoles(normalizeList(policy.getApproveRoles()));
        policy.setPublishRoles(normalizeList(policy.getPublishRoles()));
        policy.setRollbackRoles(normalizeList(policy.getRollbackRoles()));
        if (policy.getSubmitRoles().isEmpty()) policy.setSubmitRoles(defaultApprovalPolicy(skillCode).getSubmitRoles());
        if (policy.getApproveRoles().isEmpty()) policy.setApproveRoles(defaultApprovalPolicy(skillCode).getApproveRoles());
        if (policy.getPublishRoles().isEmpty()) policy.setPublishRoles(defaultApprovalPolicy(skillCode).getPublishRoles());
        if (policy.getRollbackRoles().isEmpty()) policy.setRollbackRoles(defaultApprovalPolicy(skillCode).getRollbackRoles());
        policy.setUpdatedBy(firstNonBlank(operatorId, "system"));
        policy.setUpdatedAt(now());
        return repository.upsertApprovalPolicy(policy);
    }

    public AgentSkillDiffResult diffVersion(String skillCode, Integer baseVersion, int targetVersion) {
        String code = normalize(skillCode);
        AgentSkillVersion target = requireVersion(code, targetVersion);
        AgentSkillDefinition targetDefinition = target.getDefinition() == null ? new AgentSkillDefinition() : target.getDefinition();
        AgentSkillDefinition baseDefinition;
        AgentSkillLifecycleStatus baseStatus;
        int resolvedBaseVersion = baseVersion == null ? 0 : Math.max(0, baseVersion);
        if (resolvedBaseVersion > 0) {
            AgentSkillVersion base = requireVersion(code, resolvedBaseVersion);
            baseDefinition = base.getDefinition() == null ? new AgentSkillDefinition() : base.getDefinition();
            baseStatus = base.getStatus();
        } else {
            baseDefinition = repository.findByCode(code).orElseGet(AgentSkillDefinition::new);
            baseStatus = AgentSkillLifecycleStatus.PUBLISHED;
        }

        List<AgentSkillDiffEntry> entries = new ArrayList<>();
        addScalarDiff(entries, "displayName", baseDefinition.getDisplayName(), targetDefinition.getDisplayName(), false);
        addScalarDiff(entries, "domain", baseDefinition.getDomain(), targetDefinition.getDomain(), true);
        addScalarDiff(entries, "riskLevel", baseDefinition.getRiskLevel(), targetDefinition.getRiskLevel(), riskIncreased(baseDefinition.getRiskLevel(), targetDefinition.getRiskLevel()));
        addScalarDiff(entries, "requiresHumanApproval", String.valueOf(baseDefinition.isRequiresHumanApproval()), String.valueOf(targetDefinition.isRequiresHumanApproval()), baseDefinition.isRequiresHumanApproval() != targetDefinition.isRequiresHumanApproval());
        addScalarDiff(entries, "maskingRequired", String.valueOf(baseDefinition.isMaskingRequired()), String.valueOf(targetDefinition.isMaskingRequired()), baseDefinition.isMaskingRequired() != targetDefinition.isMaskingRequired());
        addScalarDiff(entries, "enabled", String.valueOf(baseDefinition.isEnabled()), String.valueOf(targetDefinition.isEnabled()), baseDefinition.isEnabled() && !targetDefinition.isEnabled());
        addListDiff(entries, "providers", baseDefinition.getProviders(), targetDefinition.getProviders(), true);
        addListDiff(entries, "taskTypes", baseDefinition.getTaskTypes(), targetDefinition.getTaskTypes(), true);
        addListDiff(entries, "operations", baseDefinition.getOperations(), targetDefinition.getOperations(), true);
        addListDiff(entries, "toolPolicies", baseDefinition.getToolPolicies(), targetDefinition.getToolPolicies(), true);
        addListDiff(entries, "resourceScopes", baseDefinition.getResourceScopes(), targetDefinition.getResourceScopes(), true);
        addListDiff(entries, "dataClasses", baseDefinition.getDataClasses(), targetDefinition.getDataClasses(), false);

        AgentSkillDiffResult result = new AgentSkillDiffResult();
        result.setSkillCode(code);
        result.setBaseVersion(resolvedBaseVersion);
        result.setTargetVersion(targetVersion);
        result.setBaseStatus(baseStatus);
        result.setTargetStatus(target.getStatus());
        result.setEntries(entries);
        result.setChangedFields(entries.stream().map(AgentSkillDiffEntry::getField).distinct().toList());
        result.setBreakingFields(entries.stream().filter(AgentSkillDiffEntry::isBreakingChange).map(AgentSkillDiffEntry::getField).distinct().toList());
        result.setBreakingChange(!result.getBreakingFields().isEmpty());
        result.setSummary(entries.isEmpty() ? "No definition changes detected." : entries.size() + " field(s) changed; breaking=" + result.isBreakingChange());
        result.setGeneratedAt(now());
        return result;
    }

    public AgentSkillImpactAnalysisResult analyzeImpact(String skillCode, int versionNumber) {
        String code = normalize(skillCode);
        AgentSkillVersion target = requireVersion(code, versionNumber);
        AgentSkillDiffResult diff = diffVersion(code, null, versionNumber);
        List<AgentApprovedSkill> grants = repository.findApprovedSkillsBySkillCode(code, true);
        List<AgentSkillImpactAgent> impactedAgents = grants.stream().map(grant -> {
            AgentSkillImpactAgent impacted = new AgentSkillImpactAgent();
            impacted.setAgentId(grant.getAgentId());
            impacted.setSkillCode(grant.getSkillCode());
            impacted.setPolicyVersion(grant.getPolicyVersion());
            impacted.setEnabled(grant.isEnabled());
            impacted.setApprovedBy(grant.getApprovedBy());
            impacted.setApprovedAt(grant.getApprovedAt());
            impacted.setImpactReason(diff.isBreakingChange() ? "Approved Agent may lose or change dispatch eligibility after this skill version is published." : "Approved Agent references this skill; dispatch behavior should be reviewed.");
            return impacted;
        }).toList();

        AgentSkillDefinition definition = target.getDefinition() == null ? new AgentSkillDefinition() : target.getDefinition();
        List<String> notes = new ArrayList<>();
        if (diff.isBreakingChange()) notes.add("Breaking skill definition change detected before publication.");
        if (!impactedAgents.isEmpty()) notes.add(impactedAgents.size() + " approved Agent grant(s) reference this skill.");
        if (definition.isMaskingRequired()) notes.add("Masking is required for this skill; dispatch contracts must preserve data protection policy.");

        AgentSkillImpactAnalysisResult result = new AgentSkillImpactAnalysisResult();
        result.setSkillCode(code);
        result.setVersion(versionNumber);
        result.setBreakingChange(diff.isBreakingChange());
        result.setSeverity(diff.isBreakingChange() ? "HIGH" : impactedAgents.isEmpty() ? "LOW" : "MEDIUM");
        result.setImpactedAgents(impactedAgents);
        result.setImpactedAgentIds(impactedAgents.stream().map(AgentSkillImpactAgent::getAgentId).distinct().toList());
        result.setImpactedTaskTypes(definition.getTaskTypes());
        result.setImpactedProviders(definition.getProviders());
        result.setImpactedDataClasses(definition.getDataClasses());
        result.setImpactedToolPolicies(definition.getToolPolicies());
        result.setNotes(notes);
        result.setDiff(diff);
        result.setGeneratedAt(now());
        return result;
    }


    @Transactional
    public AgentSkillWorkflowResult createDraftVersion(String skillCode, AgentSkillWorkflowCommand command) {
        AgentSkillWorkflowCommand body = command == null ? new AgentSkillWorkflowCommand() : command;
        AgentSkillDefinition definition = body.getDefinition() == null ? repository.findByCode(normalize(skillCode)).orElseGet(AgentSkillDefinition::new) : body.getDefinition();
        definition.setSkillCode(skillCode);
        AgentSkillDefinition normalizedDefinition = normalizeDefinition(definition);
        int versionNumber = repository.nextVersion(normalize(skillCode));
        AgentSkillVersion version = new AgentSkillVersion();
        version.setSkillCode(normalize(skillCode));
        version.setVersion(versionNumber);
        version.setStatus(AgentSkillLifecycleStatus.DRAFT);
        version.setDefinition(normalizedDefinition);
        version.setMetadata(body.getMetadata());
        version = repository.upsertVersion(version);
        AgentSkillAuditEntry audit = audit(version, "DRAFT_CREATED", null, AgentSkillLifecycleStatus.DRAFT, body);
        return workflowResult(version, audit, "Skill draft version created.");
    }

    @Transactional
    public AgentSkillWorkflowResult submitVersion(String skillCode, int versionNumber, AgentSkillWorkflowCommand command) {
        AgentSkillVersion version = requireVersion(skillCode, versionNumber);
        AgentSkillLifecycleStatus from = version.getStatus();
        if (from != AgentSkillLifecycleStatus.DRAFT && from != AgentSkillLifecycleStatus.REJECTED) {
            throw new IllegalStateException("Only DRAFT or REJECTED skill versions can be submitted for approval.");
        }
        AgentSkillWorkflowCommand body = command == null ? new AgentSkillWorkflowCommand() : command;
        enforceWorkflowRole("SUBMIT", body, version);
        version.setStatus(AgentSkillLifecycleStatus.PENDING_APPROVAL);
        version.setSubmittedBy(firstNonBlank(body.getOperatorId(), "system"));
        version.setSubmittedAt(now());
        version = repository.upsertVersion(version);
        AgentSkillAuditEntry audit = audit(version, "SUBMITTED_FOR_APPROVAL", from, AgentSkillLifecycleStatus.PENDING_APPROVAL, body);
        return workflowResult(version, audit, "Skill version submitted for approval.");
    }

    @Transactional
    public AgentSkillWorkflowResult approveVersion(String skillCode, int versionNumber, AgentSkillWorkflowCommand command) {
        AgentSkillVersion version = requireVersion(skillCode, versionNumber);
        AgentSkillLifecycleStatus from = version.getStatus();
        if (from != AgentSkillLifecycleStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only PENDING_APPROVAL skill versions can be approved.");
        }
        AgentSkillWorkflowCommand body = command == null ? new AgentSkillWorkflowCommand() : command;
        enforceWorkflowRole("APPROVE", body, version);
        version.setStatus(AgentSkillLifecycleStatus.APPROVED);
        version.setReviewedBy(firstNonBlank(body.getOperatorId(), "system"));
        version.setReviewedAt(now());
        version.setReviewComment(body.getReason());
        version = repository.upsertVersion(version);
        AgentSkillAuditEntry audit = audit(version, "APPROVED", from, AgentSkillLifecycleStatus.APPROVED, body);
        return workflowResult(version, audit, "Skill version approved.");
    }

    @Transactional
    public AgentSkillWorkflowResult rejectVersion(String skillCode, int versionNumber, AgentSkillWorkflowCommand command) {
        AgentSkillVersion version = requireVersion(skillCode, versionNumber);
        AgentSkillLifecycleStatus from = version.getStatus();
        if (from != AgentSkillLifecycleStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only PENDING_APPROVAL skill versions can be rejected.");
        }
        AgentSkillWorkflowCommand body = command == null ? new AgentSkillWorkflowCommand() : command;
        enforceWorkflowRole("APPROVE", body, version);
        version.setStatus(AgentSkillLifecycleStatus.REJECTED);
        version.setReviewedBy(firstNonBlank(body.getOperatorId(), "system"));
        version.setReviewedAt(now());
        version.setReviewComment(body.getReason());
        version = repository.upsertVersion(version);
        AgentSkillAuditEntry audit = audit(version, "REJECTED", from, AgentSkillLifecycleStatus.REJECTED, body);
        return workflowResult(version, audit, "Skill version rejected.");
    }

    @Transactional
    public AgentSkillWorkflowResult publishVersion(String skillCode, int versionNumber, AgentSkillWorkflowCommand command) {
        AgentSkillVersion version = requireVersion(skillCode, versionNumber);
        AgentSkillLifecycleStatus from = version.getStatus();
        if (from != AgentSkillLifecycleStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED skill versions can be published.");
        }
        AgentSkillWorkflowCommand body = command == null ? new AgentSkillWorkflowCommand() : command;
        enforceWorkflowRole("PUBLISH", body, version);
        AgentSkillDefinition definition = normalizeDefinition(version.getDefinition());
        Map<String, Object> metadata = new LinkedHashMap<>(definition.getMetadata());
        metadata.put("publishedVersion", version.getVersion());
        metadata.put("lifecycleStatus", "PUBLISHED");
        definition.setMetadata(metadata);
        AgentSkillDefinition published = repository.upsert(definition);
        version.setDefinition(published);
        version.setStatus(AgentSkillLifecycleStatus.PUBLISHED);
        version.setPublishedBy(firstNonBlank(body.getOperatorId(), "system"));
        version.setPublishedAt(now());
        version = repository.upsertVersion(version);
        AgentSkillAuditEntry audit = audit(version, "PUBLISHED", from, AgentSkillLifecycleStatus.PUBLISHED, body);
        return workflowResult(version, audit, "Skill version published to active Skill Registry.");
    }

    @Transactional
    public AgentSkillWorkflowResult rollbackToVersion(String skillCode, int rollbackToVersion, AgentSkillWorkflowCommand command) {
        AgentSkillVersion target = requireVersion(skillCode, rollbackToVersion);
        AgentSkillWorkflowCommand body = command == null ? new AgentSkillWorkflowCommand() : command;
        enforceWorkflowRole("ROLLBACK", body, target);
        int newVersionNumber = repository.nextVersion(normalize(skillCode));
        AgentSkillDefinition definition = normalizeDefinition(target.getDefinition());
        Map<String, Object> metadata = new LinkedHashMap<>(definition.getMetadata());
        metadata.put("rollbackOfVersion", rollbackToVersion);
        metadata.put("createdByRollback", true);
        definition.setMetadata(metadata);
        AgentSkillVersion rollback = new AgentSkillVersion();
        rollback.setSkillCode(normalize(skillCode));
        rollback.setVersion(newVersionNumber);
        rollback.setStatus(AgentSkillLifecycleStatus.PUBLISHED);
        rollback.setDefinition(repository.upsert(definition));
        rollback.setRollbackOfVersion(rollbackToVersion);
        rollback.setPublishedBy(firstNonBlank(body.getOperatorId(), "system"));
        rollback.setPublishedAt(now());
        rollback.setMetadata(body.getMetadata());
        rollback = repository.upsertVersion(rollback);
        AgentSkillAuditEntry audit = audit(rollback, "ROLLED_BACK", target.getStatus(), AgentSkillLifecycleStatus.PUBLISHED, body);
        return workflowResult(rollback, audit, "Skill Registry rolled back by publishing a new version from the selected historical version.");
    }


    private void enforceWorkflowRole(String action, AgentSkillWorkflowCommand command, AgentSkillVersion version) {
        AgentSkillWorkflowCommand body = command == null ? new AgentSkillWorkflowCommand() : command;
        String operator = firstNonBlank(body.getOperatorId(), "system");
        if ("system".equalsIgnoreCase(operator)) {
            return;
        }
        AgentSkillApprovalPolicy policy = getApprovalPolicy(version.getSkillCode());
        if (!policy.isEnabled()) {
            return;
        }
        List<String> requiredRoles = switch (action) {
            case "SUBMIT" -> policy.getSubmitRoles();
            case "APPROVE" -> policy.getApproveRoles();
            case "PUBLISH" -> policy.getPublishRoles();
            case "ROLLBACK" -> policy.getRollbackRoles();
            default -> List.of();
        };
        if (!hasAnyRole(body.getOperatorRoles(), requiredRoles)) {
            throw new IllegalStateException("Operator lacks required Skill Registry workflow role for " + action + ": required=" + requiredRoles);
        }
        if (policy.isSeparationOfDuties() && ("APPROVE".equals(action) || "PUBLISH".equals(action))
                && operator.equalsIgnoreCase(firstNonBlank(version.getSubmittedBy(), ""))) {
            throw new IllegalStateException("Skill Registry separation-of-duties policy blocks submitter from approving or publishing the same version.");
        }
    }

    private boolean hasAnyRole(List<String> actualRoles, List<String> requiredRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty()) return true;
        Set<String> actual = new LinkedHashSet<>(normalizeList(actualRoles));
        return requiredRoles.stream().map(this::normalize).anyMatch(actual::contains);
    }

    private AgentSkillApprovalPolicy defaultApprovalPolicy(String skillCode) {
        AgentSkillApprovalPolicy policy = new AgentSkillApprovalPolicy();
        policy.setSkillCode(normalize(skillCode));
        policy.setEnabled(true);
        policy.setSubmitRoles(List.of("SYSADMIN", "ADMIN", "COMPLIANCE"));
        policy.setApproveRoles(List.of("SYSADMIN", "ADMIN", "COMPLIANCE"));
        policy.setPublishRoles(List.of("SYSADMIN", "ADMIN"));
        policy.setRollbackRoles(List.of("SYSADMIN", "ADMIN"));
        policy.setSeparationOfDuties(true);
        policy.setUpdatedBy("system-default");
        policy.setUpdatedAt(now());
        return policy;
    }

    private AgentSkillVersion requireVersion(String skillCode, int versionNumber) {
        return repository.findVersion(normalize(skillCode), versionNumber)
                .orElseThrow(() -> new IllegalArgumentException("Skill version not found: " + skillCode + "#" + versionNumber));
    }

    private AgentSkillAuditEntry audit(AgentSkillVersion version, String action, AgentSkillLifecycleStatus from, AgentSkillLifecycleStatus to, AgentSkillWorkflowCommand command) {
        AgentSkillWorkflowCommand body = command == null ? new AgentSkillWorkflowCommand() : command;
        AgentSkillAuditEntry entry = new AgentSkillAuditEntry();
        entry.setSkillCode(version.getSkillCode());
        entry.setVersion(version.getVersion());
        entry.setAction(action);
        entry.setOperatorId(firstNonBlank(body.getOperatorId(), "system"));
        entry.setReason(body.getReason());
        entry.setFromStatus(from);
        entry.setToStatus(to);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.putAll(body.getMetadata());
        metadata.put("taxonomyVersion", TAXONOMY_VERSION);
        entry.setMetadata(metadata);
        entry.setCreatedAt(now());
        return repository.appendAuditEntry(entry);
    }

    private AgentSkillWorkflowResult workflowResult(AgentSkillVersion version, AgentSkillAuditEntry audit, String message) {
        AgentSkillWorkflowResult result = new AgentSkillWorkflowResult();
        result.setSkillCode(version.getSkillCode());
        result.setVersion(version.getVersion());
        result.setStatus(version.getStatus());
        result.setDefinition(version.getDefinition());
        result.setAuditEntry(audit);
        result.setMessage(message);
        result.setOccurredAt(now());
        return result;
    }

    public List<AgentApprovedSkill> getApprovedSkills(String agentId, boolean enabledOnly) {
        if (blank(agentId)) return List.of();
        return repository.findApprovedSkills(normalize(agentId), enabledOnly);
    }

    public List<String> getApprovedSkillCodes(String agentId) {
        return getApprovedSkills(agentId, true).stream()
                .map(AgentApprovedSkill::getSkillCode)
                .filter(value -> !blank(value))
                .map(this::normalize)
                .distinct()
                .toList();
    }

    @Transactional
    public List<AgentApprovedSkill> replaceApprovedSkills(String agentId, AgentApprovedSkillSyncCommand command, AgentProfile profile) {
        if (blank(agentId)) {
            throw new IllegalArgumentException("agentId is required");
        }
        AgentApprovedSkillSyncCommand request = command == null ? new AgentApprovedSkillSyncCommand() : command;
        OffsetDateTime now = now();
        String operator = firstNonBlank(request.getOperatorId(), "system");
        int policyVersion = profile == null ? 1 : Math.max(1, profile.getPolicyVersion());
        List<AgentApprovedSkill> grants = normalizeList(request.getSkillCodes()).stream()
                .filter(skillCode -> repository.findByCode(skillCode).map(AgentSkillDefinition::isEnabled).orElse(true))
                .map(skillCode -> {
                    AgentApprovedSkill grant = new AgentApprovedSkill();
                    grant.setAgentId(normalize(agentId));
                    grant.setSkillCode(skillCode);
                    grant.setPolicyVersion(policyVersion);
                    grant.setEnabled(request.getEnabled() == null || request.getEnabled());
                    grant.setApprovedBy(operator);
                    grant.setApprovedAt(now);
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("source", "ADMIN_SKILL_SYNC");
                    metadata.put("reason", request.getReason());
                    grant.setMetadata(metadata);
                    return grant;
                })
                .toList();
        return repository.replaceApprovedSkills(normalize(agentId), grants);
    }

    public AgentApprovedSkillSyncResult previewApprovedSkillSync(String agentId, AgentProfile profile, List<String> requestedSkillCodes) {
        return buildSyncResult(agentId, profile, requestedSkillCodes, false, "Preview only");
    }

    public AgentApprovedSkillSyncResult buildSyncResult(String agentId, AgentProfile profile, List<String> requestedSkillCodes, boolean profileCapabilitiesSynced, String reason) {
        List<String> profileCapabilities = approvedSkillCodes(profile);
        List<String> tableSkills = getApprovedSkillCodes(agentId);
        LinkedHashSet<String> targetApproved = new LinkedHashSet<>(tableSkills);
        normalizeList(requestedSkillCodes).forEach(targetApproved::add);
        profileCapabilities.stream().filter(this::knownSkillOrTaskType).forEach(targetApproved::add);

        LinkedHashSet<String> targetProfileCapabilities = new LinkedHashSet<>(profileCapabilities);
        targetApproved.forEach(targetProfileCapabilities::add);

        AgentApprovedSkillSyncResult result = new AgentApprovedSkillSyncResult();
        result.setAgentId(normalize(agentId));
        result.setApprovedSkillCodes(new ArrayList<>(targetApproved));
        result.setProfileCapabilityCodes(new ArrayList<>(targetProfileCapabilities));
        result.setAddedToApprovedSkills(targetApproved.stream().filter(value -> !tableSkills.contains(value)).toList());
        result.setAddedToProfileCapabilities(targetProfileCapabilities.stream().filter(value -> !profileCapabilities.contains(value)).toList());
        result.setProfileCapabilitiesSynced(profileCapabilitiesSynced);
        result.setReason(reason);
        result.setSyncedAt(now());
        return result;
    }

    public AgentSkillEvaluationResult evaluate(AgentProfile profile, List<AgentRuntimeCapabilityItem> runtimeItems, AgentSkillEvaluationRequest request) {
        AgentSkillEvaluationRequest body = request == null ? new AgentSkillEvaluationRequest() : request;
        List<String> approved = approvedSkillCodes(profile);
        if (profile != null) {
            LinkedHashSet<String> merged = new LinkedHashSet<>(approved);
            merged.addAll(getApprovedSkillCodes(profile.getAgentId()));
            approved = new ArrayList<>(merged);
        }
        List<String> reported = reportedSkillCodes(runtimeItems);
        // Admin/Core approval is the dispatch source of truth. Runtime-reported skill or
        // capability facts are diagnostics only and must not subtract approved capabilities.
        Set<String> effective = new LinkedHashSet<>(approved);

        List<String> missing = new ArrayList<>();
        if (profile == null) {
            missing.add("coreProfile");
        } else {
            if (profile.getApprovalStatus() != AgentApprovalStatus.APPROVED) missing.add("approvalStatus:APPROVED");
            if (!profile.isEnabled()) missing.add("enabled:true");
            if (profile.getRiskStatus() != null && profile.getRiskStatus() != AgentRiskStatus.NORMAL) missing.add("riskStatus:NORMAL");
        }
        List<String> requiredCapabilities = normalizeList(body.getRequiredCapabilities());
        for (String required : requiredCapabilities) {
            if (!effective.contains(required)) missing.add("capability:" + required);
        }
        boolean directCapabilityContractSatisfied = !requiredCapabilities.isEmpty()
                && requiredCapabilities.stream().allMatch(effective::contains);

        List<AgentSkillDefinition> candidateSkills = search(body.getDomain(), true).stream()
                .filter(skill -> effective.contains(normalize(skill.getSkillCode())) || intersects(skill.getTaskTypes(), effective))
                .filter(skill -> matchesSkill(skill, body, missing))
                .toList();

        // P3-Y: a direct Admin-managed Capability Catalog contract is sufficient. Domain,
        // provider and tool-policy hints are routing metadata; they must not block a custom
        // capability unless the operator explicitly modelled a Skill Registry taxonomy contract.
        if (!directCapabilityContractSatisfied) {
            if (!blank(body.getTaskType()) && !containsAnySkillTaskType(candidateSkills, body.getTaskType()) && !effective.contains(normalize(body.getTaskType()))) {
                missing.add("taskType:" + normalize(body.getTaskType()));
            }
            if (!blank(body.getProvider()) && !containsAnySkillProvider(candidateSkills, body.getProvider()) && !effective.contains(normalize(body.getProvider()))) {
                missing.add("provider:" + normalize(body.getProvider()));
            }
            if (!blank(body.getRequiredToolPolicy()) && !containsAnySkillToolPolicy(candidateSkills, body.getRequiredToolPolicy()) && !effective.contains(normalize(body.getRequiredToolPolicy()))) {
                missing.add("toolPolicy:" + normalize(body.getRequiredToolPolicy()));
            }
        }
        LinkedHashSet<String> matchedSet = new LinkedHashSet<>();
        candidateSkills.stream().map(AgentSkillDefinition::getSkillCode).forEach(matchedSet::add);
        requiredCapabilities.stream().filter(effective::contains).forEach(matchedSet::add);
        List<String> matched = matchedSet.stream().toList();
        boolean eligible = missing.isEmpty() && (!matched.isEmpty() || directCapabilityContractSatisfied);
        AgentSkillEvaluationResult result = new AgentSkillEvaluationResult();
        result.setAgentId(profile == null ? null : profile.getAgentId());
        result.setTaxonomyVersion(TAXONOMY_VERSION);
        result.setApprovedSkillCodes(approved);
        result.setReportedSkillCodes(reported);
        result.setEffectiveSkillCodes(new ArrayList<>(effective));
        result.setMatchedSkillCodes(matched);
        result.setMissingRequirements(missing.stream().distinct().toList());
        result.setEligible(eligible);
        result.setReason(eligible ? "Agent matches Core-approved Admin-managed capabilities and the requested dispatch contract." : "Agent does not satisfy the skill dispatch contract.");
        result.setEvaluatedAt(now());
        return result;
    }

    private boolean knownSkillOrTaskType(String value) {
        String normalized = normalize(value);
        if (blank(normalized)) return false;
        if (repository.findByCode(normalized).isPresent()) return true;
        return search(null, true).stream().anyMatch(skill -> contains(skill.getTaskTypes(), normalized));
    }





    public AgentSkillDependencyGraph dependencyGraph(String skillCode, int depth) {
        String root = normalize(skillCode);
        int maxDepth = Math.max(1, Math.min(5, depth));
        List<AgentSkillDependencyEdge> edges = repository.findDependencyEdges(root, maxDepth);
        LinkedHashSet<String> nodes = new LinkedHashSet<>();
        nodes.add(root);
        for (AgentSkillDependencyEdge edge : edges) {
            if (edge == null || !edge.isEnabled()) continue;
            if (!blank(edge.getSourceSkillCode())) nodes.add(normalize(edge.getSourceSkillCode()));
            if (!blank(edge.getTargetSkillCode())) nodes.add(normalize(edge.getTargetSkillCode()));
        }
        List<String> required = edges.stream()
                .filter(edge -> edge != null && edge.isEnabled() && (edge.isRequired() || "REQUIRES".equals(normalize(edge.getRelationType()))))
                .map(AgentSkillDependencyEdge::getTargetSkillCode)
                .map(this::normalize)
                .distinct()
                .toList();
        List<String> replacements = edges.stream()
                .filter(edge -> edge != null && edge.isEnabled() && ("REPLACES".equals(normalize(edge.getRelationType())) || "SUPERSEDES".equals(normalize(edge.getRelationType())) || "ALTERNATIVE".equals(normalize(edge.getRelationType()))))
                .map(AgentSkillDependencyEdge::getTargetSkillCode)
                .map(this::normalize)
                .distinct()
                .toList();
        List<String> conflicts = edges.stream()
                .filter(edge -> edge != null && edge.isEnabled() && "CONFLICTS_WITH".equals(normalize(edge.getRelationType())))
                .map(AgentSkillDependencyEdge::getTargetSkillCode)
                .map(this::normalize)
                .distinct()
                .toList();
        List<String> warnings = new ArrayList<>();
        for (String node : nodes) {
            if (!repository.findByCode(node).isPresent()) warnings.add("Unknown skill node: " + node);
        }
        boolean cycleDetected = edges.stream().anyMatch(edge -> edge != null && edges.stream().anyMatch(other -> other != null
                && normalize(edge.getSourceSkillCode()).equals(normalize(other.getTargetSkillCode()))
                && normalize(edge.getTargetSkillCode()).equals(normalize(other.getSourceSkillCode()))));
        if (cycleDetected) warnings.add("Bidirectional dependency/cycle detected; review dispatch contract semantics before publishing.");

        AgentSkillDependencyGraph graph = new AgentSkillDependencyGraph();
        graph.setRootSkillCode(root);
        graph.setTaxonomyVersion(TAXONOMY_VERSION);
        graph.setDepth(maxDepth);
        graph.setNodes(new ArrayList<>(nodes));
        graph.setEdges(edges);
        graph.setRequiredSkillCodes(required);
        graph.setReplacementSkillCodes(replacements);
        graph.setConflictSkillCodes(conflicts);
        graph.setCycleDetected(cycleDetected);
        graph.setWarnings(warnings);
        graph.setGeneratedAt(now());
        return graph;
    }

    @Transactional
    public List<AgentSkillDependencyEdge> replaceDependencyEdges(String skillCode, AgentSkillDependencyCommand command) {
        if (blank(skillCode)) {
            throw new IllegalArgumentException("skillCode is required");
        }
        AgentSkillDependencyCommand body = command == null ? new AgentSkillDependencyCommand() : command;
        String source = normalize(skillCode);
        OffsetDateTime now = now();
        String operator = firstNonBlank(body.getOperatorId(), "system");
        List<AgentSkillDependencyEdge> normalizedEdges = new ArrayList<>();
        for (AgentSkillDependencyEdge input : body.getEdges() == null ? List.<AgentSkillDependencyEdge>of() : body.getEdges()) {
            if (input == null || blank(input.getTargetSkillCode())) continue;
            AgentSkillDependencyEdge edge = new AgentSkillDependencyEdge();
            edge.setEdgeId(firstNonBlank(input.getEdgeId(), source + "->" + normalize(input.getTargetSkillCode()) + ":" + firstNonBlank(normalize(input.getRelationType()), "RELATED_TO")));
            edge.setSourceSkillCode(source);
            edge.setTargetSkillCode(normalize(input.getTargetSkillCode()));
            edge.setRelationType(firstNonBlank(normalize(input.getRelationType()), "RELATED_TO"));
            edge.setRequired(input.isRequired() || "REQUIRES".equals(normalize(input.getRelationType())));
            edge.setEnabled(input.isEnabled());
            edge.setConfidence(input.getConfidence() <= 0 ? 1.0d : input.getConfidence());
            edge.setDescription(input.getDescription());
            edge.setCreatedBy(firstNonBlank(input.getCreatedBy(), operator));
            edge.setCreatedAt(input.getCreatedAt() == null ? now : input.getCreatedAt());
            edge.setUpdatedBy(operator);
            edge.setUpdatedAt(now);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.putAll(input.getMetadata());
            metadata.putAll(body.getMetadata());
            metadata.put("reason", body.getReason());
            metadata.put("taxonomyVersion", TAXONOMY_VERSION);
            edge.setMetadata(metadata);
            normalizedEdges.add(edge);
        }
        List<AgentSkillDependencyEdge> saved = repository.replaceDependencyEdges(source, normalizedEdges);
        AgentSkillAuditEntry audit = new AgentSkillAuditEntry();
        audit.setSkillCode(source);
        audit.setVersion(0);
        audit.setAction("DEPENDENCY_GRAPH_UPDATED");
        audit.setOperatorId(operator);
        audit.setReason(firstNonBlank(body.getReason(), "Skill dependency graph updated."));
        audit.setMetadata(Map.of("edgeCount", saved.size(), "taxonomyVersion", TAXONOMY_VERSION));
        audit.setCreatedAt(now);
        repository.appendAuditEntry(audit);
        return saved;
    }

    public AgentSkillRemediationProposal proposeAgentRemediation(AgentProfile profile, List<AgentRuntimeCapabilityItem> runtimeItems) {
        AgentCapabilityDriftReport report = detectAgentDrift(profile, runtimeItems);
        return remediationProposal(profile == null ? null : profile.getAgentId(), report, profile);
    }

    public AgentSkillRemediationProposal proposeFleetRemediation(List<AgentProfile> profiles, Map<String, List<AgentRuntimeCapabilityItem>> runtimeByAgent) {
        AgentCapabilityDriftReport report = detectFleetDrift(profiles == null ? List.of() : profiles, runtimeByAgent == null ? Map.of() : runtimeByAgent);
        return remediationProposal(null, report, null);
    }

    private AgentSkillRemediationProposal remediationProposal(String agentId, AgentCapabilityDriftReport report, AgentProfile profile) {
        List<AgentSkillRemediationAction> actions = new ArrayList<>();
        int index = 1;
        for (AgentCapabilityDriftItem item : report.getItems() == null ? List.<AgentCapabilityDriftItem>of() : report.getItems()) {
            if (!blank(agentId) && !normalize(agentId).equals(normalize(item.getAgentId()))) continue;
            AgentSkillRemediationAction action = remediationAction(item, profile, index++);
            if (action != null) actions.add(action);
        }
        addDependencyRemediationActions(actions, profile, index);
        List<String> summary = new ArrayList<>();
        summary.add(actions.size() + " remediation action(s) proposed from " + (report.getDriftCount()) + " drift item(s).");
        long executableCount = actions.stream().filter(AgentSkillRemediationAction::isExecutable).count();
        summary.add(executableCount + " action(s) can be executed directly by governance workflow; others require operator review or Agent/runtime change.");
        AgentSkillRemediationProposal proposal = new AgentSkillRemediationProposal();
        proposal.setAgentId(blank(agentId) ? null : normalize(agentId));
        proposal.setTaxonomyVersion(TAXONOMY_VERSION);
        proposal.setSourceDriftCount(report.getDriftCount());
        proposal.setHighSeverityCount(report.getHighSeverityCount());
        proposal.setActions(actions);
        proposal.setSummary(summary);
        proposal.setMetadata(Map.of("generatedFrom", "CAPABILITY_DRIFT_REPORT"));
        proposal.setGeneratedAt(now());
        return proposal;
    }

    private AgentSkillRemediationAction remediationAction(AgentCapabilityDriftItem item, AgentProfile profile, int index) {
        if (item == null) return null;
        String type = normalize(item.getDriftType());
        AgentSkillRemediationAction action = new AgentSkillRemediationAction();
        action.setActionId("rem-" + index);
        action.setAgentId(normalize(item.getAgentId()));
        action.setSkillCode(normalize(item.getSkillCode()));
        action.setSeverity(firstNonBlank(item.getSeverity(), "MEDIUM"));
        action.setExecutable(false);
        Map<String, Object> actionMetadata = new LinkedHashMap<>();
        actionMetadata.put("driftType", item.getDriftType());
        actionMetadata.put("detail", item.getDetail());
        action.setMetadata(actionMetadata);
        switch (type == null ? "" : type) {
            case "REPORTED_NOT_APPROVED" -> {
                action.setActionType(item.isTaxonomyKnown() && item.isTaxonomyEnabled() ? "APPROVE_REPORTED_SKILL" : "REJECT_RUNTIME_SKILL_SIGNAL");
                action.setExecutable(item.isTaxonomyKnown() && item.isTaxonomyEnabled());
                action.setReason(item.isTaxonomyKnown() && item.isTaxonomyEnabled()
                        ? "Runtime reports a known enabled skill that Core has not approved yet. Review and approve if the Agent should receive matching dispatch tasks."
                        : "Runtime reports an unknown or disabled skill. Update Agent capabilityProfile or define/enable the skill before approval.");
                action.setCommandHint(Map.of("api", "/admin/agents/{agentId}/skills/sync-approved-capabilities", "skillCodes", List.of(action.getSkillCode())));
            }
            case "APPROVED_NOT_REPORTED" -> {
                action.setActionType("REQUEST_RUNTIME_PROFILE_UPDATE");
                action.setReason("Core approved this skill, but Agent runtime did not report it. Ask Agent owner to update OpenClaw capabilityProfile or remove approval.");
                action.setPrerequisites(List.of("Verify OpenClaw plugin configuration", "Check latest heartbeat capabilityProfile"));
            }
            case "APPROVED_UNKNOWN_SKILL" -> {
                action.setActionType("REVOKE_UNKNOWN_APPROVAL");
                action.setExecutable(true);
                action.setReason("Governance approved a skill that does not exist in active taxonomy. Remove it from approved skills/profile capabilities or create the missing definition.");
                action.setCommandHint(Map.of("api", "/admin/agents/{agentId}/skills/approved", "operation", "replaceWithoutSkill"));
            }
            case "REPORTED_UNKNOWN_SKILL" -> {
                action.setActionType("CREATE_SKILL_DEFINITION_OR_UPDATE_RUNTIME");
                action.setReason("Agent reports a skill not known by Skill Registry. Either create a definition or change runtime capabilityProfile to a supported taxonomy code.");
                action.setPrerequisites(List.of("Review provider/taskType semantics", "Confirm taxonomyVersion"));
            }
            case "DISABLED_APPROVED_SKILL", "DEPRECATED_APPROVED_SKILL" -> {
                List<String> replacements = item.getReplacementSkillCodes() == null ? List.of() : item.getReplacementSkillCodes();
                action.setActionType(replacements.isEmpty() ? "REVOKE_DISABLED_OR_DEPRECATED_SKILL" : "MIGRATE_APPROVED_SKILL");
                action.setTargetSkillCode(replacements.isEmpty() ? null : normalize(replacements.get(0)));
                action.setExecutable(!replacements.isEmpty());
                action.setReason(replacements.isEmpty()
                        ? "Approved skill is disabled/deprecated with no replacement. Revoke after operator review."
                        : "Approved skill is deprecated. Migrate Agent approval to replacement skill before deprecation completes.");
                action.setCommandHint(Map.of("replacementSkillCodes", replacements, "api", "/admin/agents/{agentId}/skills/sync-approved-capabilities"));
            }
            case "DISABLED_REPORTED_SKILL", "DEPRECATED_REPORTED_SKILL" -> {
                action.setActionType("UPDATE_RUNTIME_PROFILE_TO_REPLACEMENT");
                action.setTargetSkillCode((item.getReplacementSkillCodes() == null || item.getReplacementSkillCodes().isEmpty()) ? null : normalize(item.getReplacementSkillCodes().get(0)));
                action.setReason("Agent runtime still reports disabled/deprecated skill. Update OpenClaw plugin profile to replacement or remove the capability.");
            }
            case "NO_SKILL_SIGNAL" -> {
                action.setActionType("REQUEST_STRUCTURED_SKILL_PROFILE");
                action.setReason("Agent has no structured skill signal. Upgrade OpenClaw capabilityProfile to opensocket-capability-taxonomy/v1 skills[].");
                action.setPrerequisites(List.of("OpenClaw plugin P9 capabilityProfile contract"));
            }
            default -> {
                action.setActionType("REVIEW_DRIFT_MANUALLY");
                action.setReason(firstNonBlank(item.getSuggestedAction(), "Manual review required."));
            }
        }
        return action;
    }

    private void addDependencyRemediationActions(List<AgentSkillRemediationAction> actions, AgentProfile profile, int startIndex) {
        if (profile == null || blank(profile.getAgentId())) return;
        Set<String> approved = new LinkedHashSet<>(approvedSkillCodes(profile));
        approved.addAll(getApprovedSkillCodes(profile.getAgentId()));
        if (approved.isEmpty()) return;
        int index = startIndex;
        List<AgentSkillDependencyEdge> edges = repository.findAllDependencyEdges();
        for (AgentSkillDependencyEdge edge : edges) {
            if (edge == null || !edge.isEnabled()) continue;
            String source = normalize(edge.getSourceSkillCode());
            String target = normalize(edge.getTargetSkillCode());
            String relation = normalize(edge.getRelationType());
            if (!approved.contains(source)) continue;
            if ((edge.isRequired() || "REQUIRES".equals(relation)) && !approved.contains(target)) {
                AgentSkillRemediationAction action = new AgentSkillRemediationAction();
                action.setActionId("rem-dep-" + index++);
                action.setAgentId(normalize(profile.getAgentId()));
                action.setSkillCode(source);
                action.setTargetSkillCode(target);
                action.setActionType("ADD_REQUIRED_DEPENDENCY_SKILL");
                action.setSeverity("HIGH");
                action.setExecutable(true);
                action.setReason("Approved skill depends on another skill that is not approved for this Agent.");
                action.setCommandHint(Map.of("api", "/admin/agents/{agentId}/skills/sync-approved-capabilities", "skillCodes", List.of(source, target)));
                actions.add(action);
            }
            if ("CONFLICTS_WITH".equals(relation) && approved.contains(target)) {
                AgentSkillRemediationAction action = new AgentSkillRemediationAction();
                action.setActionId("rem-conflict-" + index++);
                action.setAgentId(normalize(profile.getAgentId()));
                action.setSkillCode(source);
                action.setTargetSkillCode(target);
                action.setActionType("REVIEW_CONFLICTING_SKILL_APPROVALS");
                action.setSeverity("HIGH");
                action.setExecutable(false);
                action.setReason("Agent has two approved skills that are marked as conflicting in the dependency graph.");
                actions.add(action);
            }
        }
    }


    public AgentSkillDeprecationPlan getDeprecationPlan(String skillCode) {
        return repository.findDeprecationPlan(normalize(skillCode)).orElseGet(() -> defaultDeprecationPlan(skillCode));
    }

    public List<AgentSkillDeprecationPlan> listDeprecationPlans(String status) {
        return repository.listDeprecationPlans(normalize(status));
    }

    @Transactional
    public AgentSkillDeprecationPlan upsertDeprecationPlan(String skillCode, AgentSkillDeprecationCommand command) {
        if (blank(skillCode)) {
            throw new IllegalArgumentException("skillCode is required");
        }
        AgentSkillDeprecationCommand body = command == null ? new AgentSkillDeprecationCommand() : command;
        AgentSkillDeprecationPlan existing = repository.findDeprecationPlan(normalize(skillCode)).orElse(null);
        AgentSkillDeprecationPlan plan = existing == null ? new AgentSkillDeprecationPlan() : existing;
        OffsetDateTime now = now();
        String operator = firstNonBlank(body.getOperatorId(), "system");
        plan.setSkillCode(normalize(skillCode));
        plan.setStatus(firstNonBlank(normalize(body.getStatus()), plan.getStatus(), "PLANNED"));
        plan.setReplacementSkillCodes(normalizeList(body.getReplacementSkillCodes()));
        plan.setMigrationDeadline(body.getMigrationDeadline());
        if (plan.getCreatedAt() == null) plan.setCreatedAt(now);
        if (blank(plan.getCreatedBy())) plan.setCreatedBy(operator);
        plan.setUpdatedBy(operator);
        plan.setUpdatedAt(now);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.putAll(plan.getMetadata());
        metadata.putAll(body.getMetadata());
        metadata.put("reason", body.getReason());
        metadata.put("taxonomyVersion", TAXONOMY_VERSION);
        plan.setMetadata(metadata);
        return repository.upsertDeprecationPlan(plan);
    }

    public AgentCapabilityDriftReport detectAgentDrift(AgentProfile profile, List<AgentRuntimeCapabilityItem> runtimeItems) {
        if (profile == null) {
            AgentCapabilityDriftReport report = new AgentCapabilityDriftReport();
            report.setTaxonomyVersion(TAXONOMY_VERSION);
            report.setGeneratedAt(now());
            return report;
        }
        return buildDriftReport(List.of(profile), Map.of(profile.getAgentId(), runtimeItems == null ? List.of() : runtimeItems), profile.getAgentId());
    }

    public AgentCapabilityDriftReport detectFleetDrift(List<AgentProfile> profiles, Map<String, List<AgentRuntimeCapabilityItem>> runtimeByAgent) {
        return buildDriftReport(profiles == null ? List.of() : profiles, runtimeByAgent == null ? Map.of() : runtimeByAgent, null);
    }

    public AgentSkillDeprecationMigrationPlan analyzeDeprecationMigrationPlan(String skillCode, List<AgentProfile> profiles, Map<String, List<AgentRuntimeCapabilityItem>> runtimeByAgent) {
        String code = normalize(skillCode);
        AgentSkillDeprecationPlan storedPlan = getDeprecationPlan(code);
        List<AgentApprovedSkill> grants = repository.findApprovedSkillsBySkillCode(code, true);
        AgentCapabilityDriftReport driftReport = detectFleetDrift(profiles == null ? List.of() : profiles, runtimeByAgent == null ? Map.of() : runtimeByAgent);
        List<AgentCapabilityDriftItem> relevantDrifts = driftReport.getItems().stream()
                .filter(item -> code.equals(normalize(item.getSkillCode())))
                .toList();
        LinkedHashSet<String> impactedAgentIds = new LinkedHashSet<>();
        grants.stream().map(AgentApprovedSkill::getAgentId).filter(value -> !blank(value)).map(this::normalize).forEach(impactedAgentIds::add);
        relevantDrifts.stream().map(AgentCapabilityDriftItem::getAgentId).filter(value -> !blank(value)).map(this::normalize).forEach(impactedAgentIds::add);

        List<String> blockers = new ArrayList<>();
        if (!repository.findByCode(code).isPresent()) blockers.add("Skill definition does not exist in active taxonomy.");
        if (normalizeList(storedPlan.getReplacementSkillCodes()).isEmpty()) blockers.add("At least one replacement skill is recommended before deprecation.");
        for (String replacement : normalizeList(storedPlan.getReplacementSkillCodes())) {
            if (!repository.findByCode(replacement).map(AgentSkillDefinition::isEnabled).orElse(false)) {
                blockers.add("Replacement skill is missing or disabled: " + replacement);
            }
        }

        List<String> steps = new ArrayList<>();
        steps.add("Create or review deprecation plan for " + code + ".");
        if (!storedPlan.getReplacementSkillCodes().isEmpty()) steps.add("Map affected Agents to replacement skills: " + storedPlan.getReplacementSkillCodes());
        steps.add("Run capability drift scan and resolve APPROVED_NOT_REPORTED / REPORTED_NOT_APPROVED before deprecation.");
        steps.add("Synchronize approved skills and governance capabilities for impacted Agents.");
        steps.add("Publish a new skill version with enabled=false only after migration blockers are cleared.");

        AgentSkillDeprecationMigrationPlan result = new AgentSkillDeprecationMigrationPlan();
        result.setSkillCode(code);
        result.setStatus(firstNonBlank(storedPlan.getStatus(), "PLANNED"));
        result.setDeprecated(isDeprecatedPlan(storedPlan));
        result.setCanDeprecate(blockers.isEmpty());
        result.setReplacementSkillCodes(storedPlan.getReplacementSkillCodes());
        result.setImpactedAgentIds(new ArrayList<>(impactedAgentIds));
        result.setImpactedAgents(grants.stream().map(grant -> {
            AgentSkillImpactAgent impacted = new AgentSkillImpactAgent();
            impacted.setAgentId(grant.getAgentId());
            impacted.setSkillCode(grant.getSkillCode());
            impacted.setPolicyVersion(grant.getPolicyVersion());
            impacted.setEnabled(grant.isEnabled());
            impacted.setApprovedBy(grant.getApprovedBy());
            impacted.setApprovedAt(grant.getApprovedAt());
            impacted.setImpactReason("Agent has an approved skill grant that must migrate before deprecation.");
            return impacted;
        }).toList());
        result.setDriftItems(relevantDrifts);
        result.setMigrationSteps(steps);
        result.setBlockingReasons(blockers);
        result.setSeverity(!blockers.isEmpty() ? "HIGH" : impactedAgentIds.isEmpty() ? "LOW" : "MEDIUM");
        result.setMetadata(storedPlan.getMetadata());
        result.setGeneratedAt(now());
        return result;
    }

    private AgentCapabilityDriftReport buildDriftReport(List<AgentProfile> profiles, Map<String, List<AgentRuntimeCapabilityItem>> runtimeByAgent, String singleAgentId) {
        Map<String, AgentSkillDefinition> definitions = search(null, false).stream()
                .collect(LinkedHashMap::new, (map, skill) -> map.put(normalize(skill.getSkillCode()), skill), LinkedHashMap::putAll);
        Map<String, String> taskTypeToSkill = new LinkedHashMap<>();
        for (AgentSkillDefinition skill : definitions.values()) {
            for (String taskType : normalizeList(skill.getTaskTypes())) {
                taskTypeToSkill.put(taskType, normalize(skill.getSkillCode()));
            }
        }
        Map<String, AgentSkillDeprecationPlan> deprecationPlans = repository.listDeprecationPlans(null).stream()
                .collect(LinkedHashMap::new, (map, plan) -> map.put(normalize(plan.getSkillCode()), plan), LinkedHashMap::putAll);
        List<AgentCapabilityDriftItem> items = new ArrayList<>();
        int aligned = 0;
        for (AgentProfile profile : profiles == null ? List.<AgentProfile>of() : profiles) {
            if (profile == null || blank(profile.getAgentId())) continue;
            String agentId = normalize(profile.getAgentId());
            List<String> approved = approvedSkillCodes(profile);
            LinkedHashSet<String> approvedSet = new LinkedHashSet<>();
            for (String value : approved) {
                String skillValue = normalize(resolveSkillCode(value, definitions, taskTypeToSkill));
                if (!blank(skillValue)) approvedSet.add(skillValue);
            }
            getApprovedSkillCodes(agentId).forEach(approvedSet::add);

            LinkedHashSet<String> reportedSet = reportedTaxonomySkillCodes(runtimeByAgent.get(agentId), definitions, taskTypeToSkill);
            LinkedHashSet<String> union = new LinkedHashSet<>(approvedSet);
            union.addAll(reportedSet);
            if (union.isEmpty()) {
                items.add(drift(agentId, null, "NO_SKILL_SIGNAL", "MEDIUM", false, false, false, false, false, List.of(), "Report structured skills in capabilityProfile.skills and approve matching Core skills.", "No approved or runtime reported taxonomy skill was found for this Agent."));
                continue;
            }
            for (String skillCode : union) {
                AgentSkillDefinition definition = definitions.get(skillCode);
                AgentSkillDeprecationPlan deprecationPlan = deprecationPlans.get(skillCode);
                boolean approvedFlag = approvedSet.contains(skillCode);
                boolean reportedFlag = reportedSet.contains(skillCode);
                boolean known = definition != null;
                boolean enabled = definition == null || definition.isEnabled();
                boolean deprecated = isDeprecatedPlan(deprecationPlan);
                List<String> replacements = deprecationPlan == null ? List.of() : deprecationPlan.getReplacementSkillCodes();
                if (!known) {
                    items.add(drift(agentId, skillCode, approvedFlag ? "APPROVED_UNKNOWN_SKILL" : "REPORTED_UNKNOWN_SKILL", "HIGH", approvedFlag, reportedFlag, false, false, false, replacements, "Create Skill Registry definition or remove this skill from runtime/approved capability.", "Skill does not exist in active taxonomy."));
                } else if (!enabled) {
                    items.add(drift(agentId, skillCode, approvedFlag ? "DISABLED_APPROVED_SKILL" : "DISABLED_REPORTED_SKILL", "HIGH", approvedFlag, reportedFlag, true, false, deprecated, replacements, "Migrate to enabled replacement skill and remove disabled skill from Agent policy/runtime.", "Skill exists but is disabled in active taxonomy."));
                } else if (deprecated) {
                    items.add(drift(agentId, skillCode, approvedFlag ? "DEPRECATED_APPROVED_SKILL" : "DEPRECATED_REPORTED_SKILL", "HIGH", approvedFlag, reportedFlag, true, true, true, replacements, "Follow deprecation migration plan before removing this skill.", "Skill has an active deprecation plan."));
                } else if (approvedFlag && !reportedFlag) {
                    items.add(drift(agentId, skillCode, "APPROVED_NOT_REPORTED", "MEDIUM", true, false, true, true, false, replacements, "Update plugin capabilityProfile.skills or remove stale approved grant.", "Core approves this skill, but runtime did not report it."));
                } else if (!approvedFlag && reportedFlag) {
                    items.add(drift(agentId, skillCode, "REPORTED_NOT_APPROVED", "MEDIUM", false, true, true, true, false, replacements, "Review and approve the skill, or remove it from plugin runtime profile.", "Runtime reports this skill, but Core has not approved it."));
                } else {
                    aligned++;
                }
            }
        }
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (AgentCapabilityDriftItem item : items) {
            typeCounts.merge(item.getDriftType(), 1, Integer::sum);
        }
        AgentCapabilityDriftReport report = new AgentCapabilityDriftReport();
        report.setAgentId(singleAgentId == null ? null : normalize(singleAgentId));
        report.setTaxonomyVersion(TAXONOMY_VERSION);
        report.setScannedAgents(profiles == null ? 0 : profiles.size());
        report.setAlignedCount(aligned);
        report.setDriftCount(items.size());
        report.setHighSeverityCount((int) items.stream().filter(item -> "HIGH".equalsIgnoreCase(item.getSeverity())).count());
        report.setDriftTypeCounts(typeCounts);
        report.setItems(items.stream().sorted(Comparator.comparing(AgentCapabilityDriftItem::getAgentId, Comparator.nullsLast(String::compareTo)).thenComparing(AgentCapabilityDriftItem::getDriftType, Comparator.nullsLast(String::compareTo))).toList());
        report.setGeneratedAt(now());
        return report;
    }

    private AgentCapabilityDriftItem drift(String agentId, String skillCode, String driftType, String severity, boolean approved, boolean reported, boolean taxonomyKnown, boolean taxonomyEnabled, boolean deprecated, List<String> replacementSkillCodes, String suggestedAction, String detail) {
        AgentCapabilityDriftItem item = new AgentCapabilityDriftItem();
        item.setAgentId(agentId);
        item.setSkillCode(skillCode);
        item.setDriftType(driftType);
        item.setSeverity(severity);
        item.setApproved(approved);
        item.setReported(reported);
        item.setTaxonomyKnown(taxonomyKnown);
        item.setTaxonomyEnabled(taxonomyEnabled);
        item.setDeprecated(deprecated);
        item.setReplacementSkillCodes(normalizeList(replacementSkillCodes));
        item.setSuggestedAction(suggestedAction);
        item.setDetail(detail);
        item.setMetadata(Map.of("taxonomyVersion", TAXONOMY_VERSION));
        item.setDetectedAt(now());
        return item;
    }

    private LinkedHashSet<String> reportedTaxonomySkillCodes(List<AgentRuntimeCapabilityItem> items, Map<String, AgentSkillDefinition> definitions, Map<String, String> taskTypeToSkill) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (items == null) return values;
        for (AgentRuntimeCapabilityItem item : items) {
            if (item == null || blank(item.capabilityValue())) continue;
            String kind = normalize(item.capabilityKind());
            String resolved = resolveSkillCode(item.capabilityValue(), definitions, taskTypeToSkill);
            if (!blank(resolved) && (definitions.containsKey(normalize(resolved)) || (kind != null && kind.contains("SKILL")))) {
                values.add(normalize(resolved));
            }
        }
        return values;
    }

    private String resolveSkillCode(String value, Map<String, AgentSkillDefinition> definitions, Map<String, String> taskTypeToSkill) {
        String normalized = normalize(value);
        if (blank(normalized)) return null;
        if (definitions.containsKey(normalized)) return normalized;
        return taskTypeToSkill.getOrDefault(normalized, normalized);
    }

    private boolean isDeprecatedPlan(AgentSkillDeprecationPlan plan) {
        if (plan == null) return false;
        String status = normalize(plan.getStatus());
        return "PLANNED".equals(status) || "MIGRATING".equals(status) || "DEPRECATED".equals(status);
    }

    private AgentSkillDeprecationPlan defaultDeprecationPlan(String skillCode) {
        AgentSkillDeprecationPlan plan = new AgentSkillDeprecationPlan();
        plan.setSkillCode(normalize(skillCode));
        plan.setStatus("NONE");
        plan.setReplacementSkillCodes(List.of());
        plan.setCreatedBy("system-default");
        plan.setUpdatedBy("system-default");
        plan.setCreatedAt(now());
        plan.setUpdatedAt(now());
        plan.setMetadata(Map.of("default", true, "taxonomyVersion", TAXONOMY_VERSION));
        return plan;
    }

    private AgentSkillDefinition normalizeDefinition(AgentSkillDefinition input) {
        OffsetDateTime now = now();
        AgentSkillDefinition normalized = new AgentSkillDefinition();
        normalized.setSkillCode(normalize(input.getSkillCode()));
        normalized.setDisplayName(firstNonBlank(input.getDisplayName(), normalize(input.getSkillCode())));
        normalized.setDomain(normalize(input.getDomain()));
        normalized.setDescription(input.getDescription());
        normalized.setTaxonomyVersion(firstNonBlank(input.getTaxonomyVersion(), TAXONOMY_VERSION));
        normalized.setTaskDefinitionId(firstNonBlank(input.getTaskDefinitionId(), stringValue(input.getMetadata().get("taskDefinitionId"))));
        normalized.setSourceSystem(firstNonBlank(normalize(input.getSourceSystem()), normalize(input.getDomain()), normalize(stringValue(input.getMetadata().get("sourceSystem")))));
        normalized.setTaskType(firstNonBlank(normalize(input.getTaskType()), firstOf(normalizeList(input.getTaskTypes())), normalize(stringValue(input.getMetadata().get("taskType")))));
        normalized.setProviders(normalizeList(input.getProviders()));
        normalized.setTaskTypes(normalizeList(input.getTaskTypes()));
        normalized.setOperations(normalizeList(input.getOperations()));
        normalized.setToolPolicies(normalizeList(input.getToolPolicies()));
        normalized.setResourceScopes(normalizeList(input.getResourceScopes()));
        normalized.setDataClasses(normalizeList(input.getDataClasses()));
        normalized.setRiskLevel(firstNonBlank(normalize(input.getRiskLevel()), "LOW"));
        normalized.setRequiresHumanApproval(input.isRequiresHumanApproval());
        normalized.setMaskingRequired(input.isMaskingRequired());
        normalized.setEnabled(input.isEnabled());
        normalized.setMetadata(input.getMetadata());
        normalized.setCreatedAt(input.getCreatedAt());
        normalized.setUpdatedAt(now);
        return normalized;
    }

    private boolean matchesSkill(AgentSkillDefinition skill, AgentSkillEvaluationRequest request, List<String> missing) {
        if (!blank(request.getDomain()) && !normalize(request.getDomain()).equals(normalize(skill.getDomain()))) return false;
        if (!blank(request.getTaskType()) && !contains(skill.getTaskTypes(), request.getTaskType())) return false;
        if (!blank(request.getProvider()) && !contains(skill.getProviders(), request.getProvider())) return false;
        if (!blank(request.getOperation()) && !contains(skill.getOperations(), request.getOperation())) return false;
        if (!blank(request.getRequiredToolPolicy()) && !contains(skill.getToolPolicies(), request.getRequiredToolPolicy())) return false;
        for (String dataClass : normalizeList(request.getDataClasses())) {
            if (!contains(skill.getDataClasses(), dataClass)) {
                missing.add("dataClass:" + dataClass);
            }
        }
        return true;
    }

    private List<String> approvedSkillCodes(AgentProfile profile) {
        if (profile == null || profile.getCapabilities() == null) return List.of();
        return profile.getCapabilities().stream()
                .filter(capability -> capability != null && capability.isEnabled())
                .map(AgentCapability::getCapabilityCode)
                .filter(value -> !blank(value))
                .map(this::normalize)
                .distinct()
                .toList();
    }

    private List<String> reportedSkillCodes(List<AgentRuntimeCapabilityItem> items) {
        if (items == null) return List.of();
        return items.stream()
                .filter(item -> item != null && !blank(item.capabilityValue()))
                .map(AgentRuntimeCapabilityItem::capabilityValue)
                .map(this::normalize)
                .distinct()
                .toList();
    }


    private void addScalarDiff(List<AgentSkillDiffEntry> entries, String field, String before, String after, boolean breaking) {
        String left = before == null ? "" : before;
        String right = after == null ? "" : after;
        if (left.equals(right)) return;
        AgentSkillDiffEntry entry = new AgentSkillDiffEntry();
        entry.setField(field);
        entry.setChangeType("CHANGED");
        entry.setBeforeValues(List.of(left));
        entry.setAfterValues(List.of(right));
        entry.setBreakingChange(breaking);
        entry.setNote(breaking ? "Field change may affect dispatch eligibility or governance risk." : "Field changed.");
        entries.add(entry);
    }

    private void addListDiff(List<AgentSkillDiffEntry> entries, String field, List<String> before, List<String> after, boolean removalIsBreaking) {
        LinkedHashSet<String> left = new LinkedHashSet<>(normalizeList(before));
        LinkedHashSet<String> right = new LinkedHashSet<>(normalizeList(after));
        if (left.equals(right)) return;
        List<String> removed = left.stream().filter(value -> !right.contains(value)).toList();
        List<String> added = right.stream().filter(value -> !left.contains(value)).toList();
        AgentSkillDiffEntry entry = new AgentSkillDiffEntry();
        entry.setField(field);
        entry.setChangeType(!removed.isEmpty() && !added.isEmpty() ? "CHANGED" : !removed.isEmpty() ? "REMOVED" : "ADDED");
        entry.setBeforeValues(new ArrayList<>(left));
        entry.setAfterValues(new ArrayList<>(right));
        entry.setRemovedValues(removed);
        entry.setAddedValues(added);
        entry.setBreakingChange(removalIsBreaking && !removed.isEmpty());
        entry.setNote(entry.isBreakingChange() ? "Removed value may make previously eligible dispatch contracts fail." : "List values changed.");
        entries.add(entry);
    }

    private boolean riskIncreased(String before, String after) {
        return riskRank(after) > riskRank(before);
    }

    private int riskRank(String value) {
        return switch (normalize(value) == null ? "LOW" : normalize(value)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private boolean containsAnySkillTaskType(List<AgentSkillDefinition> skills, String taskType) {
        return skills.stream().anyMatch(skill -> contains(skill.getTaskTypes(), taskType));
    }

    private boolean containsAnySkillProvider(List<AgentSkillDefinition> skills, String provider) {
        return skills.stream().anyMatch(skill -> contains(skill.getProviders(), provider));
    }

    private boolean containsAnySkillToolPolicy(List<AgentSkillDefinition> skills, String policy) {
        return skills.stream().anyMatch(skill -> contains(skill.getToolPolicies(), policy));
    }

    private boolean intersects(List<String> left, Set<String> right) {
        if (left == null || right == null) return false;
        for (String value : left) {
            if (right.contains(normalize(value))) return true;
        }
        return false;
    }

    private boolean contains(List<String> values, String value) {
        String normalized = normalize(value);
        return values != null && !blank(normalized) && values.stream().map(this::normalize).anyMatch(normalized::equals);
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> !blank(value)).map(this::normalize).distinct().toList();
    }

    private String normalize(String value) {
        return blank(value) ? null : value.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!blank(value)) return value;
        }
        return null;
    }

    private String firstOf(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
