package com.opensocket.aievent.core.agent.readiness;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityItem;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.assignment.AgentAssignmentService;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignment;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignmentStatus;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityCatalog;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentCapability;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceRepository;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentRiskStatus;
import com.opensocket.aievent.core.agent.skill.AgentSkillDefinition;
import com.opensocket.aievent.core.agent.skill.AgentSkillEvaluationRequest;
import com.opensocket.aievent.core.agent.skill.AgentSkillEvaluationResult;
import com.opensocket.aievent.core.agent.skill.AgentSkillRegistryService;
import com.opensocket.aievent.core.agent.skill.TaskDispatchContractResolveRequest;
import com.opensocket.aievent.core.agent.skill.TaskDispatchContractResolveResult;
import com.opensocket.aievent.core.agent.skill.TaskDispatchContractResolverService;

@Service
public class DispatchReadinessEvaluationService {
    private final AgentDirectoryService agentDirectoryService;
    private final AgentGovernanceRepository governanceRepository;
    private final AgentSkillRegistryService skillRegistryService;
    private final TaskDispatchContractResolverService contractResolverService;
    private final AgentAssignmentService assignmentService;

    public DispatchReadinessEvaluationService(AgentDirectoryService agentDirectoryService,
                                              AgentGovernanceRepository governanceRepository,
                                              AgentSkillRegistryService skillRegistryService,
                                              TaskDispatchContractResolverService contractResolverService) {
        this(agentDirectoryService, governanceRepository, skillRegistryService, contractResolverService, null);
    }

    @Autowired
    public DispatchReadinessEvaluationService(AgentDirectoryService agentDirectoryService,
                                              AgentGovernanceRepository governanceRepository,
                                              AgentSkillRegistryService skillRegistryService,
                                              TaskDispatchContractResolverService contractResolverService,
                                              AgentAssignmentService assignmentService) {
        this.agentDirectoryService = agentDirectoryService;
        this.governanceRepository = governanceRepository;
        this.skillRegistryService = skillRegistryService;
        this.contractResolverService = contractResolverService;
        this.assignmentService = assignmentService;
    }

    public DispatchReadinessEvaluationResult evaluate(DispatchReadinessEvaluationRequest request) {
        DispatchReadinessEvaluationRequest body = request == null ? new DispatchReadinessEvaluationRequest() : request;
        String tenantId = requireNonBlank(body.getTenantId(), "tenantId");
        String agentId = trim(body.getAgentId());
        List<String> rawTaskRequirements = normalizeList(body.getRequiredCapabilities());
        Optional<AgentSnapshot> agent = blank(agentId) ? Optional.empty() : agentDirectoryService.findAgent(agentId);
        AgentProfile profile = blank(agentId) ? null : governanceRepository.findProfile(agentId).orElse(null);
        List<AgentRuntimeCapabilityItem> runtimeItems = blank(agentId) ? List.of() : agentDirectoryService.findRuntimeCapabilityItems(agentId);

        TaskDispatchContractResolveResult contract = contractResolverService.resolve(toContractRequest(body));
        List<String> effectiveCapabilities = effectiveDispatchCapabilities(rawTaskRequirements, contract);
        List<String> legacyTaskAliases = legacyTaskAliases(rawTaskRequirements, effectiveCapabilities);
        AgentProfile skillEvaluationProfile = enrichProfileWithApprovedCapabilityAssignments(agentId, profile);
        AgentSkillEvaluationResult skillEvaluation = skillRegistryService.evaluate(skillEvaluationProfile, runtimeItems, toSkillRequest(body, effectiveCapabilities));

        List<DispatchReadinessCheck> checks = new ArrayList<>();
        checks.add(taskRequiresCapabilitiesCheck(rawTaskRequirements));
        checks.add(effectiveCapabilityContractCheck(rawTaskRequirements, effectiveCapabilities, legacyTaskAliases));
        checks.add(skillDefinedCheck(tenantId, effectiveCapabilities));
        checks.add(contractResolvedCheck(contract, effectiveCapabilities));
        checks.add(governanceProfileCheck(agentId, profile));
        checks.add(governanceCapabilityCheck(agentId, profile, effectiveCapabilities));
        checks.add(runtimeAgentCheck(agentId, agent));
        checks.add(runtimeCapabilityCheck(agentId, runtimeItems, agent.orElse(null), effectiveCapabilities));
        checks.add(capacityCheck(agent.orElse(null)));
        checks.add(skillEvaluationCheck(skillEvaluation));

        boolean ready = checks.stream().noneMatch(check -> check.getStatus() == DispatchReadinessStatus.FAIL);
        DispatchReadinessEvaluationResult result = new DispatchReadinessEvaluationResult();
        result.setReady(ready);
        result.setAgentId(agentId);
        result.setRequiredCapabilities(effectiveCapabilities);
        result.setRawTaskRequirements(rawTaskRequirements);
        result.setEffectiveDispatchCapabilities(effectiveCapabilities);
        result.setLegacyTaskAliases(legacyTaskAliases);
        result.setContractResolution(contract);
        result.setSkillEvaluation(skillEvaluation);
        result.setMatchedSkillCodes(skillEvaluation.getMatchedSkillCodes());
        result.setMissingRequirements(skillEvaluation.getMissingRequirements());
        result.setChecks(checks);
        result.setRecommendedActions(checks.stream()
                .map(DispatchReadinessCheck::getFixAction)
                .filter(action -> action != null)
                .toList());
        result.setSummary(ready ? "Agent is dispatch-ready for the requested task contract." : "Agent is not dispatch-ready for the requested task contract.");
        result.setBeginnerSummary(ready
                ? "可以派工：Capability contract、Agent 授權、Runtime 連線/容量與 Task 需求都已對齊。"
                : "不可派工：請依照失敗項目的建議操作修正 Capability、Agent 授權、Dispatch Flow coverage 或 Runtime 連線/容量。"
        );
        result.setLabels(labelMap(rawTaskRequirements, effectiveCapabilities, legacyTaskAliases, contract, skillEvaluation));
        result.setEvaluatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return result;
    }

    private DispatchReadinessCheck taskRequiresCapabilitiesCheck(List<String> requiredCapabilities) {
        if (requiredCapabilities.isEmpty()) {
            DispatchReadinessCheck check = DispatchReadinessCheck.fail(
                    "TASK_REQUIRES_CAPABILITY",
                    "任務需要的能力",
                    "Task 沒有宣告 requiredCapabilities，Routing 無法知道要找哪種 Agent。"
            );
            check.setBeginnerHint("若此 Flow 使用 EXPLICIT 模式，請在 Capability Catalog 與 Flow Rule 指定能力；若只是分析型工作，請使用 SOURCE_DEFAULT 並設定 Source Default 與 Agent Source Coverage。");
            return check;
        }
        DispatchReadinessCheck check = DispatchReadinessCheck.pass(
                "TASK_REQUIRES_CAPABILITY",
                "任務需要的能力",
                "Task 需要 " + String.join(", ", requiredCapabilities) + "。"
        );
        check.setBeginnerHint("這代表工作單已說清楚它需要哪種 Agent 能力。只有具備相同能力的 Agent 才會被考慮。"
        );
        check.setEvidence(requiredCapabilities);
        return check;
    }

    private DispatchReadinessCheck skillDefinedCheck(String tenantId, List<String> requiredCapabilities) {
        List<String> missing = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        List<AgentSkillDefinition> skills = skillRegistryService.search(null, true);
        Set<String> catalogCapabilities = capabilityCatalogCodes(tenantId);
        for (String required : requiredCapabilities) {
            String normalized = normalize(required);
            boolean found = catalogCapabilities.contains(normalized) || skills.stream().anyMatch(skill -> skillMatches(required, skill));
            if (found) matched.add(required); else missing.add(required);
        }
        if (!missing.isEmpty()) {
            DispatchReadinessCheck check = DispatchReadinessCheck.fail(
                    "CAPABILITY_DEFINED",
                    "Capability catalog is defined",
                    "Capability Catalog does not contain these effective capabilities: " + String.join(", ", missing)
            );
            check.setBeginnerHint("Capability Catalog is the Admin-managed dispatch vocabulary. Create or enable these capabilities; a Skill Registry taxonomy definition is optional."
            );
            DispatchReadinessFixAction action = new DispatchReadinessFixAction("Create suggested capability definition", "UPSERT_CAPABILITY_DEFINITION", "/settings/capabilities");
            action.setPayload(Map.of("capabilityCodes", missing));
            check.setFixAction(action);
            check.setEvidence(missing);
            return check;
        }
        DispatchReadinessCheck check = DispatchReadinessCheck.pass(
                "CAPABILITY_DEFINED",
                "Capability catalog is defined",
                matched.isEmpty() ? "No effective dispatch capabilities to check." : "Capability Catalog contains: " + String.join(", ", matched)
        );
        check.setBeginnerHint("This means Admin UI/Core knows these reusable capabilities. Skill Registry taxonomy is optional and must not block custom capabilities."
        );
        check.setEvidence(matched);
        return check;
    }

    private DispatchReadinessCheck contractResolvedCheck(TaskDispatchContractResolveResult contract, List<String> effectiveCapabilities) {
        if (contract == null || !contract.isResolved()) {
            DispatchReadinessCheck check = DispatchReadinessCheck.fail(
                    "DISPATCH_CONTRACT_RESOLVED",
                    "Dispatch capability contract resolved",
                    "Dispatch contract could not resolve an effective capability contract."
            );
            check.setBeginnerHint("Check taskType/domain/provider/operation/raw requirements against Capability Catalog definitions."
            );
            if (contract != null) check.setEvidence(contract.getResolutionReasons());
            return check;
        }
        DispatchReadinessCheck check = DispatchReadinessCheck.pass(
                "DISPATCH_CONTRACT_RESOLVED",
                "Dispatch capability contract resolved",
                "Dispatch contract resolved effective capabilities: " + String.join(", ", effectiveCapabilities)
        );
        check.setBeginnerHint("This means the raw task requirement was converted into the effective capability contract used by governance and runtime checks."
        );
        check.setEvidence(effectiveCapabilities);
        return check;
    }

    private DispatchReadinessCheck governanceProfileCheck(String agentId, AgentProfile profile) {
        if (blank(agentId)) {
            DispatchReadinessCheck check = DispatchReadinessCheck.fail("GOVERNANCE_PROFILE", "Agent 治理狀態", "沒有提供 agentId。");
            check.setBeginnerHint("請先選一個 Agent，才能檢查它是否被公司核准接任務。"
            );
            return check;
        }
        if (profile == null) {
            DispatchReadinessCheck check = DispatchReadinessCheck.fail("GOVERNANCE_PROFILE", "Agent 治理狀態", "Core Governance 找不到 Agent profile：" + agentId);
            check.setBeginnerHint("請先讓 Agent 完成 enrollment / approval，或用測試 bootstrap 建立 Agent profile。"
            );
            return check;
        }
        List<String> failures = new ArrayList<>();
        if (profile.getApprovalStatus() != AgentApprovalStatus.APPROVED) failures.add("approvalStatus=" + profile.getApprovalStatus());
        if (!profile.isEnabled()) failures.add("enabled=false");
        if (profile.getRiskStatus() != null && profile.getRiskStatus() != AgentRiskStatus.NORMAL) failures.add("riskStatus=" + profile.getRiskStatus());
        if (!failures.isEmpty()) {
            DispatchReadinessCheck check = DispatchReadinessCheck.fail("GOVERNANCE_PROFILE", "Agent 治理狀態", "Agent 尚未通過治理檢查：" + String.join(", ", failures));
            check.setBeginnerHint("即使 Agent runtime 自稱有能力，只要 Core Governance 未核准、停用或風險異常，就不能派工。"
            );
            check.setEvidence(failures);
            return check;
        }
        DispatchReadinessCheck check = DispatchReadinessCheck.pass("GOVERNANCE_PROFILE", "Agent 治理狀態", "Agent 已 APPROVED、enabled=true、riskStatus=NORMAL。");
        check.setBeginnerHint("這代表公司允許這個 Agent 參與派工。"
        );
        check.setEvidence(List.of("APPROVED", "enabled=true", "NORMAL"));
        return check;
    }

    private DispatchReadinessCheck governanceCapabilityCheck(String agentId, AgentProfile profile, List<String> requiredCapabilities) {
        Set<String> approved = new LinkedHashSet<>();
        if (!blank(agentId) && assignmentService != null) {
            assignmentService.findAgentCapabilities(agentId).stream()
                    .filter(assignment -> assignment != null && assignment.getStatus() == AgentCapabilityAssignmentStatus.APPROVED)
                    .map(AgentCapabilityAssignment::getCapabilityCode)
                    .map(this::normalize)
                    .filter(value -> !blank(value))
                    .forEach(approved::add);
        }
        // Test-only fallback for existing focused unit tests that construct this service without
        // the assignmentService. Production readiness uses governed capability assignments.
        if (approved.isEmpty() && profile != null) addCapabilities(approved, profile.getCapabilities());
        Set<String> normalizedApproved = normalizeSet(approved);
        List<String> missing = requiredCapabilities.stream().filter(required -> !normalizedApproved.contains(required)).toList();
        if (!missing.isEmpty()) {
            DispatchReadinessCheck check = DispatchReadinessCheck.fail("GOVERNANCE_APPROVED_CAPABILITY", "Core-approved capability is present", "Core has not approved these effective capabilities for this Agent: " + String.join(", ", missing));
            check.setBeginnerHint("Approve these capabilities in Agent Detail > Capabilities. Do not approve legacy task aliases unless they are also real reusable capabilities."
            );
            DispatchReadinessFixAction action = new DispatchReadinessFixAction("Approve effective capabilities for this Agent", "APPROVE_AGENT_CAPABILITY", "/agents/{agentId}");
            action.setPayload(Map.of("agentId", agentId == null ? "" : agentId, "capabilityCodes", missing, "syncProfileCapabilities", true));
            check.setFixAction(action);
            check.setEvidence(new ArrayList<>(normalizedApproved));
            return check;
        }
        DispatchReadinessCheck check = DispatchReadinessCheck.pass("GOVERNANCE_APPROVED_CAPABILITY", "Core-approved capability is present", requiredCapabilities.isEmpty() ? "No effective dispatch capabilities to check." : "Core governance approved every effective capability required by the resolved contract.");
        check.setBeginnerHint("This means Core allows this Agent to use the capabilities required by the task."
        );
        check.setEvidence(new ArrayList<>(normalizedApproved));
        return check;
    }

    private DispatchReadinessCheck runtimeAgentCheck(String agentId, Optional<AgentSnapshot> agent) {
        if (blank(agentId)) {
            return DispatchReadinessCheck.fail("RUNTIME_AGENT_ONLINE", "Agent Runtime 已連線", "沒有提供 agentId。");
        }
        if (agent.isEmpty()) {
            DispatchReadinessCheck check = DispatchReadinessCheck.fail("RUNTIME_AGENT_ONLINE", "Agent Runtime 已連線", "Core Agent Directory 找不到 runtime snapshot：" + agentId);
            check.setBeginnerHint("請確認 Agent 是否已連到 Netty Gateway，或 Core/Netty directory sync 是否正常。"
            );
            return check;
        }
        AgentSnapshot snapshot = agent.get();
        if (snapshot.getStatus() == null || !snapshot.getStatus().isAssignable()) {
            DispatchReadinessCheck check = DispatchReadinessCheck.fail("RUNTIME_AGENT_ONLINE", "Agent Runtime 已連線", "Agent runtime 狀態目前不可派工：" + snapshot.getStatus());
            check.setBeginnerHint("新手可先確認 Agent 是否為 IDLE 或 BUSY_ACCEPTING。OFFLINE/EXPIRED/DRAINING 都不應派工。"
            );
            check.setEvidence(List.of(String.valueOf(snapshot.getStatus())));
            return check;
        }
        DispatchReadinessCheck check = DispatchReadinessCheck.pass("RUNTIME_AGENT_ONLINE", "Agent Runtime 已連線", "Agent runtime 狀態可派工：" + snapshot.getStatus());
        check.setBeginnerHint("這代表 Agent 現在有連上 runtime，且狀態允許接任務。"
        );
        check.setEvidence(List.of(String.valueOf(snapshot.getStatus())));
        return check;
    }

    private DispatchReadinessCheck runtimeCapabilityCheck(String agentId, List<AgentRuntimeCapabilityItem> runtimeItems, AgentSnapshot agent, List<String> requiredCapabilities) {
        Set<String> reported = runtimeCapabilities(runtimeItems, agent);
        List<String> missing = requiredCapabilities.stream().filter(required -> !reported.contains(required)).toList();
        DispatchReadinessCheck check = DispatchReadinessCheck.pass(
                "RUNTIME_REPORTED_CAPABILITY",
                "Runtime capability observation is optional",
                missing.isEmpty()
                        ? "Runtime observations are aligned, but dispatch uses Core-approved Admin-managed capabilities as the source of truth."
                        : "Runtime did not report optional capability observations: " + String.join(", ", missing) + ". This does not block dispatch because capabilities are administered in Core.");
        check.setBeginnerHint("Dispatch capability eligibility is managed by Admin UI/Core. Runtime heartbeats prove connection and capacity; they are not the source of truth for what tasks an Agent may receive."
        );
        check.setEvidence(new ArrayList<>(reported));
        return check;
    }

    private DispatchReadinessCheck capacityCheck(AgentSnapshot agent) {
        if (agent == null) {
            DispatchReadinessCheck check = DispatchReadinessCheck.fail("AGENT_CAPACITY_AVAILABLE", "Agent 有可用容量", "沒有 runtime snapshot，無法確認容量。");
            check.setBeginnerHint("請先讓 Agent 連線。"
            );
            return check;
        }
        if (!agent.isAssignable()) {
            DispatchReadinessCheck check = DispatchReadinessCheck.fail("AGENT_CAPACITY_AVAILABLE", "Agent 有可用容量", "Agent 目前不可接任務，可能離線、draining、backoff 或 slots 已滿。");
            check.setBeginnerHint("請確認 status、availableSlots、runtimeBackoffUntil 與 draining 狀態。"
            );
            check.setEvidence(List.of("status=" + agent.getStatus(), "availableSlots=" + agent.getAvailableSlots(), "effectiveTaskCount=" + agent.getEffectiveTaskCount(), "maxConcurrentTasks=" + agent.getMaxConcurrentTasks()));
            return check;
        }
        DispatchReadinessCheck check = DispatchReadinessCheck.pass("AGENT_CAPACITY_AVAILABLE", "Agent 有可用容量", "Agent 有可用容量，可接受派工。");
        check.setBeginnerHint("這代表 Agent 不只是在線，也還有空接任務。"
        );
        check.setEvidence(List.of("availableSlots=" + agent.getAvailableSlots(), "effectiveTaskCount=" + agent.getEffectiveTaskCount(), "maxConcurrentTasks=" + agent.getMaxConcurrentTasks()));
        return check;
    }

    private DispatchReadinessCheck skillEvaluationCheck(AgentSkillEvaluationResult evaluation) {
        if (evaluation == null || !evaluation.isEligible()) {
            DispatchReadinessCheck check = DispatchReadinessCheck.fail("CAPABILITY_CONTRACT_ELIGIBLE", "Capability contract eligibility passed", evaluation == null ? "Capability contract evaluation failed." : evaluation.getReason());
            check.setBeginnerHint("This final check uses the effective capability contract and Core-approved Admin-managed capabilities. Runtime capability self-reporting is diagnostic only."
            );
            if (evaluation != null) check.setEvidence(evaluation.getMissingRequirements());
            return check;
        }
        DispatchReadinessCheck check = DispatchReadinessCheck.pass("CAPABILITY_CONTRACT_ELIGIBLE", "Capability contract eligibility passed", evaluation.getReason());
        check.setBeginnerHint("This means this Agent can be considered a candidate for the resolved capability contract."
        );
        check.setEvidence(evaluation.getMatchedSkillCodes());
        return check;
    }

    private AgentSkillEvaluationRequest toSkillRequest(DispatchReadinessEvaluationRequest request, List<String> effectiveCapabilities) {
        AgentSkillEvaluationRequest body = new AgentSkillEvaluationRequest();
        body.setDomain(request.getDomain());
        body.setProvider(request.getProvider());
        String normalizedTaskType = normalize(request.getTaskType());
        body.setTaskType(effectiveCapabilities != null && effectiveCapabilities.contains(normalizedTaskType) ? request.getTaskType() : null);
        body.setSiteCode(request.getSiteCode());
        body.setOperation(request.getOperation());
        body.setRequiredToolPolicy(request.getRequiredToolPolicy());
        body.setRequiredCapabilities(effectiveCapabilities);
        body.setDataClasses(request.getDataClasses());
        return body;
    }

    private TaskDispatchContractResolveRequest toContractRequest(DispatchReadinessEvaluationRequest request) {
        TaskDispatchContractResolveRequest body = new TaskDispatchContractResolveRequest();
        body.setTaskId(request.getTaskId());
        body.setTaskType(request.getTaskType());
        body.setDomain(request.getDomain());
        body.setProvider(request.getProvider());
        body.setSiteCode(request.getSiteCode());
        body.setPlantId(request.getPlantId());
        body.setObjectType(request.getObjectType());
        body.setEventType(request.getEventType());
        body.setErrorCode(request.getErrorCode());
        body.setOperation(request.getOperation());
        body.setRequiredToolPolicy(request.getRequiredToolPolicy());
        body.setRequiredCapabilities(request.getRequiredCapabilities());
        body.setDataClasses(request.getDataClasses());
        body.setPayloadMetadata(request.getPayloadMetadata());
        return body;
    }

    private DispatchReadinessCheck effectiveCapabilityContractCheck(List<String> rawTaskRequirements, List<String> effectiveCapabilities, List<String> legacyTaskAliases) {
        if (effectiveCapabilities == null || effectiveCapabilities.isEmpty()) {
            DispatchReadinessCheck check = DispatchReadinessCheck.fail(
                    "EFFECTIVE_CAPABILITY_CONTRACT",
                    "Effective capability contract",
                    "No effective dispatch capability could be derived from the raw task requirement."
            );
            check.setBeginnerHint("Raw task aliases are not dispatch capabilities. Configure the task contract so it resolves to reusable capabilities.");
            check.setEvidence(rawTaskRequirements);
            return check;
        }
        DispatchReadinessCheck check = DispatchReadinessCheck.pass(
                "EFFECTIVE_CAPABILITY_CONTRACT",
                "Effective capability contract",
                legacyTaskAliases.isEmpty()
                        ? "Effective dispatch capabilities: " + String.join(", ", effectiveCapabilities)
                        : "Raw task aliases " + String.join(", ", legacyTaskAliases) + " resolved to effective capabilities: " + String.join(", ", effectiveCapabilities)
        );
        check.setBeginnerHint("Governance, runtime and eligibility checks use effective dispatch capabilities, not legacy task aliases.");
        check.setEvidence(effectiveCapabilities);
        check.setDetails(Map.of(
                "rawTaskRequirements", rawTaskRequirements == null ? List.of() : rawTaskRequirements,
                "effectiveDispatchCapabilities", effectiveCapabilities,
                "legacyTaskAliases", legacyTaskAliases == null ? List.of() : legacyTaskAliases
        ));
        return check;
    }

    private List<String> effectiveDispatchCapabilities(List<String> rawTaskRequirements, TaskDispatchContractResolveResult contract) {
        LinkedHashSet<String> effective = new LinkedHashSet<>();
        // Admin-managed direct capabilities are the authoritative task requirement.
        // matchedSkillCodes are optional taxonomy hints and must not replace a raw CMS/custom
        // capability with a hard-coded domain expansion such as MES incident response.
        if (contract != null && contract.getRequiredCapabilities() != null) {
            contract.getRequiredCapabilities().stream().map(this::normalize).filter(value -> !blank(value)).forEach(effective::add);
        }
        if (effective.isEmpty() && contract != null && contract.getMatchedSkillCodes() != null) {
            contract.getMatchedSkillCodes().stream().map(this::normalize).filter(value -> !blank(value)).forEach(effective::add);
        }
        if (effective.isEmpty() && rawTaskRequirements != null) {
            rawTaskRequirements.stream().map(this::normalize).filter(value -> !blank(value)).forEach(effective::add);
        }
        return new ArrayList<>(effective);
    }

    private List<String> legacyTaskAliases(List<String> rawTaskRequirements, List<String> effectiveCapabilities) {
        Set<String> effective = new LinkedHashSet<>(effectiveCapabilities == null ? List.of() : effectiveCapabilities);
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        if (rawTaskRequirements != null) {
            rawTaskRequirements.stream()
                    .map(this::normalize)
                    .filter(value -> !blank(value) && !effective.contains(value))
                    .forEach(aliases::add);
        }
        return new ArrayList<>(aliases);
    }

    private Set<String> capabilityCatalogCodes(String tenantId) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (assignmentService == null) return codes;
        try {
            assignmentService.searchCapabilities(requireNonBlank(tenantId, "tenantId"), "ACTIVE", null, 1000).stream()
                    .map(AgentCapabilityCatalog::getCapabilityCode)
                    .map(this::normalize)
                    .filter(value -> !blank(value))
                    .forEach(codes::add);
        } catch (Exception ignored) {
            // Keep diagnostics best-effort when the backing catalog is unavailable.
        }
        return codes;
    }

    private boolean skillMatches(String required, AgentSkillDefinition skill) {
        if (skill == null || !skill.isEnabled()) return false;
        String normalized = normalize(required);
        if (blank(normalized)) return false;
        if (normalized.equals(normalize(skill.getSkillCode()))) return true;
        return skill.getTaskTypes() != null && skill.getTaskTypes().stream().map(this::normalize).anyMatch(normalized::equals);
    }

    private AgentProfile enrichProfileWithApprovedCapabilityAssignments(String agentId, AgentProfile profile) {
        if (profile == null || assignmentService == null || blank(agentId)) {
            return profile;
        }
        LinkedHashSet<String> governedApproved = new LinkedHashSet<>();
        assignmentService.findAgentCapabilities(agentId).stream()
                .filter(assignment -> assignment != null && assignment.getStatus() == AgentCapabilityAssignmentStatus.APPROVED)
                .filter(assignment -> assignment.getExpiresAt() == null || assignment.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
                .map(AgentCapabilityAssignment::getCapabilityCode)
                .map(this::normalize)
                .filter(value -> !blank(value))
                .forEach(governedApproved::add);
        if (governedApproved.isEmpty()) {
            return profile;
        }

        AgentProfile enriched = new AgentProfile();
        enriched.setAgentId(profile.getAgentId());
        enriched.setTenantId(profile.getTenantId());
        enriched.setAgentName(profile.getAgentName());
        enriched.setAgentType(profile.getAgentType());
        enriched.setOwnerTeam(profile.getOwnerTeam());
        enriched.setDescription(profile.getDescription());
        enriched.setApprovalStatus(profile.getApprovalStatus());
        enriched.setEnabled(profile.isEnabled());
        enriched.setRiskStatus(profile.getRiskStatus());
        enriched.setPolicyVersion(profile.getPolicyVersion());
        enriched.setCreatedAt(profile.getCreatedAt());
        enriched.setUpdatedAt(profile.getUpdatedAt());
        enriched.setCredential(profile.getCredential());
        enriched.setAuthorizationScopes(profile.getAuthorizationScopes());

        List<AgentCapability> capabilities = new ArrayList<>(profile.getCapabilities() == null ? List.of() : profile.getCapabilities());
        LinkedHashSet<String> existing = new LinkedHashSet<>();
        addCapabilities(existing, capabilities);
        for (String capabilityCode : governedApproved) {
            if (existing.contains(capabilityCode)) {
                continue;
            }
            AgentCapability capability = new AgentCapability(agentId, capabilityCode);
            capability.setEnabled(true);
            capability.setApprovedBy("CORE_ADMIN_ASSIGNMENT");
            capability.setApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
            capabilities.add(capability);
        }
        enriched.setCapabilities(capabilities);
        return enriched;
    }

    private void addCapabilities(Set<String> target, List<AgentCapability> capabilities) {
        if (capabilities == null) return;
        capabilities.stream()
                .filter(capability -> capability != null && capability.isEnabled())
                .map(AgentCapability::getCapabilityCode)
                .map(this::normalize)
                .filter(value -> !blank(value))
                .forEach(target::add);
    }

    private Set<String> runtimeCapabilities(List<AgentRuntimeCapabilityItem> runtimeItems, AgentSnapshot agent) {
        LinkedHashSet<String> reported = new LinkedHashSet<>();
        if (runtimeItems != null) {
            runtimeItems.stream()
                    .filter(item -> item != null && !blank(item.capabilityValue()))
                    .map(AgentRuntimeCapabilityItem::capabilityValue)
                    .map(this::normalize)
                    .filter(value -> !blank(value))
                    .forEach(reported::add);
        }
        if (reported.isEmpty() && agent != null && agent.getCapabilities() != null) {
            agent.getCapabilities().stream().map(this::normalize).filter(value -> !blank(value)).forEach(reported::add);
        }
        return reported;
    }

    private Set<String> normalizeSet(Set<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) values.stream().map(this::normalize).filter(value -> !blank(value)).forEach(normalized::add);
        return normalized;
    }

    private List<String> normalizeList(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) values.stream().map(this::normalize).filter(value -> !blank(value)).forEach(normalized::add);
        return new ArrayList<>(normalized);
    }

    private Map<String, Object> labelMap(List<String> rawTaskRequirements, List<String> effectiveCapabilities, List<String> legacyTaskAliases, TaskDispatchContractResolveResult contract, AgentSkillEvaluationResult evaluation) {
        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("rawTaskRequirements", rawTaskRequirements);
        labels.put("effectiveDispatchCapabilities", effectiveCapabilities);
        labels.put("legacyTaskAliases", legacyTaskAliases);
        labels.put("requiredCapabilities", effectiveCapabilities);
        labels.put("matchedCapabilityCodes", evaluation == null ? List.of() : evaluation.getMatchedSkillCodes());
        labels.put("beginnerTerms", Map.of(
                "Capability Catalog", "能力目錄：系統知道有哪些可重用能力",
                "Agent Governance", "Agent 授權：公司允許 Agent 使用哪些能力",
                "Runtime Agent", "Agent 現況：Agent 現在實際回報哪些能力",
                "Task", "任務：工作單可能帶 legacy alias，但必須解析成 effective capabilities",
                "Routing", "派工檢查：effective capability、Agent 授權、runtime 連線與容量都符合才會派工；業務 capability 不要求 runtime 自報"
        ));
        if (contract != null) labels.put("contractReasons", contract.getResolutionReasons());
        return labels;
    }

    private String normalize(String value) {
        return blank(value) ? null : value.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private String requireNonBlank(String value, String fieldName) {
        if (blank(value)) throw new IllegalArgumentException(fieldName + " is required");
        return value.trim();
    }

    private String trim(String value) {
        return blank(value) ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
