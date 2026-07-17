package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityItem;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEvent;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEventType;
import com.opensocket.aievent.core.agent.skill.AgentSkillDefinition;
import com.opensocket.aievent.core.agent.skill.AgentApprovedSkill;
import com.opensocket.aievent.core.agent.skill.AgentApprovedSkillSyncCommand;
import com.opensocket.aievent.core.agent.skill.AgentApprovedSkillSyncResult;
import com.opensocket.aievent.core.agent.skill.AgentSkillEvaluationRequest;
import com.opensocket.aievent.core.agent.skill.AgentSkillEvaluationResult;
import com.opensocket.aievent.core.agent.skill.AgentSkillRegistryService;
import com.opensocket.aievent.core.agent.skill.AgentSkillVersion;
import com.opensocket.aievent.core.agent.skill.AgentSkillAuditEntry;
import com.opensocket.aievent.core.agent.skill.AgentSkillWorkflowCommand;
import com.opensocket.aievent.core.agent.skill.AgentSkillWorkflowResult;
import com.opensocket.aievent.core.agent.skill.AgentSkillImpactAnalysisResult;
import com.opensocket.aievent.core.agent.skill.AgentSkillDiffResult;
import com.opensocket.aievent.core.agent.skill.AgentSkillApprovalPolicy;
import com.opensocket.aievent.core.agent.skill.AgentCapabilityDriftReport;
import com.opensocket.aievent.core.agent.skill.AgentCapabilityDriftItem;
import com.opensocket.aievent.core.agent.skill.AgentSkillDeprecationCommand;
import com.opensocket.aievent.core.agent.skill.AgentSkillDeprecationMigrationPlan;
import com.opensocket.aievent.core.agent.skill.AgentSkillDeprecationPlan;
import com.opensocket.aievent.core.agent.skill.AgentSkillDependencyCommand;
import com.opensocket.aievent.core.agent.skill.AgentSkillDependencyEdge;
import com.opensocket.aievent.core.agent.skill.AgentSkillDependencyGraph;
import com.opensocket.aievent.core.agent.skill.AgentSkillRemediationProposal;
import com.opensocket.aievent.core.agent.skill.TaskDispatchContractResolveRequest;
import com.opensocket.aievent.core.agent.skill.TaskDispatchContractResolveResult;
import com.opensocket.aievent.core.agent.skill.TaskDispatchContractResolverService;
import com.opensocket.aievent.core.agent.governance.AgentProfileUpdateCommand;

@RestController
public class AgentSkillRegistryController {
    private final AgentSkillRegistryService skillRegistryService;
    private final AgentGovernanceService agentGovernanceService;
    private final AgentDirectoryService agentDirectoryService;
    private final TaskDispatchContractResolverService dispatchContractResolverService;

    public AgentSkillRegistryController(AgentSkillRegistryService skillRegistryService,
                                        AgentGovernanceService agentGovernanceService,
                                        AgentDirectoryService agentDirectoryService,
                                        TaskDispatchContractResolverService dispatchContractResolverService) {
        this.skillRegistryService = skillRegistryService;
        this.agentGovernanceService = agentGovernanceService;
        this.agentDirectoryService = agentDirectoryService;
        this.dispatchContractResolverService = dispatchContractResolverService;
    }

    private void requireLegacySkillWriteOverride(Map<String, Object> metadata, String message) {
        Object override = metadata == null ? null : metadata.get("p3lLegacySkillWriteOverride");
        if (Boolean.TRUE.equals(override) || "true".equalsIgnoreCase(String.valueOf(override))) {
            return;
        }
        throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, message);
    }

    @GetMapping("/admin/agent-skills/metadata")
    public Map<String, Object> metadata() {
        return Map.of(
                "taxonomyVersion", AgentSkillRegistryService.TAXONOMY_VERSION,
                "storeMode", skillRegistryService.mode());
    }

    @GetMapping("/admin/agent-skills")
    public List<AgentSkillDefinition> searchSkills(@RequestParam(required = false) String domain,
                                                   @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return skillRegistryService.search(domain, enabledOnly);
    }

    @GetMapping("/admin/agent-skills/{skillCode}")
    public AgentSkillDefinition getSkill(@PathVariable String skillCode) {
        try {
            return skillRegistryService.get(skillCode);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.NOT_FOUND, ex.getMessage());
        }
    }

    @PutMapping("/admin/agent-skills/{skillCode}")
    public AgentSkillDefinition upsertSkill(@PathVariable String skillCode, @RequestBody AgentSkillDefinition request) {
        AgentSkillDefinition body = request == null ? new AgentSkillDefinition() : request;
        body.setSkillCode(skillCode);
        requireLegacySkillWriteOverride(body.getMetadata(), "LEGACY_SKILL_REGISTRY_WRITE_BLOCKED: P3-L makes /admin/agent-skills readonly for normal dispatch policy work. Use /admin/dispatch-policies instead.");
        return skillRegistryService.upsert(body);
    }

    @DeleteMapping("/admin/agent-skills/{skillCode}")
    public Map<String, Object> deleteSkill(@PathVariable String skillCode) {
        throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, "LEGACY_SKILL_REGISTRY_DELETE_BLOCKED: P3-L keeps /admin/agent-skills for migration visibility and P9 compatibility only. Disable or migrate to Dispatch Policy v2 instead.");
    }



    @GetMapping("/admin/agent-skills/{skillCode}/versions")
    public List<AgentSkillVersion> listSkillVersions(@PathVariable String skillCode) {
        return skillRegistryService.listVersions(skillCode);
    }

    @PostMapping("/admin/agent-skills/{skillCode}/versions/draft")
    public AgentSkillWorkflowResult createSkillDraft(@PathVariable String skillCode,
                                                     @RequestBody(required = false) AgentSkillWorkflowCommand request) {
        requireLegacySkillWriteOverride(request == null ? null : request.getMetadata(), "LEGACY_SKILL_VERSION_WRITE_BLOCKED: P3-L blocks new legacy skill drafts. Use Dispatch Policy v2.");
        return skillRegistryService.createDraftVersion(skillCode, request);
    }

    @PostMapping("/admin/agent-skills/{skillCode}/versions/{version}/submit")
    public AgentSkillWorkflowResult submitSkillVersion(@PathVariable String skillCode,
                                                       @PathVariable int version,
                                                       @RequestBody(required = false) AgentSkillWorkflowCommand request) {
        requireLegacySkillWriteOverride(request == null ? null : request.getMetadata(), "LEGACY_SKILL_VERSION_WRITE_BLOCKED: P3-L blocks legacy skill workflow writes. Use Dispatch Policy v2.");
        return skillRegistryService.submitVersion(skillCode, version, request);
    }

    @PostMapping("/admin/agent-skills/{skillCode}/versions/{version}/approve")
    public AgentSkillWorkflowResult approveSkillVersion(@PathVariable String skillCode,
                                                        @PathVariable int version,
                                                        @RequestBody(required = false) AgentSkillWorkflowCommand request) {
        requireLegacySkillWriteOverride(request == null ? null : request.getMetadata(), "LEGACY_SKILL_VERSION_WRITE_BLOCKED: P3-L blocks legacy skill workflow writes. Use Dispatch Policy v2.");
        return skillRegistryService.approveVersion(skillCode, version, request);
    }

    @PostMapping("/admin/agent-skills/{skillCode}/versions/{version}/reject")
    public AgentSkillWorkflowResult rejectSkillVersion(@PathVariable String skillCode,
                                                       @PathVariable int version,
                                                       @RequestBody(required = false) AgentSkillWorkflowCommand request) {
        return skillRegistryService.rejectVersion(skillCode, version, request);
    }

    @PostMapping("/admin/agent-skills/{skillCode}/versions/{version}/publish")
    public AgentSkillWorkflowResult publishSkillVersion(@PathVariable String skillCode,
                                                        @PathVariable int version,
                                                        @RequestBody(required = false) AgentSkillWorkflowCommand request) {
        requireLegacySkillWriteOverride(request == null ? null : request.getMetadata(), "LEGACY_SKILL_VERSION_PUBLISH_BLOCKED: P3-L blocks publishing legacy skill policies. Use Dispatch Policy v2.");
        return skillRegistryService.publishVersion(skillCode, version, request);
    }

    @PostMapping("/admin/agent-skills/{skillCode}/versions/{version}/rollback")
    public AgentSkillWorkflowResult rollbackSkillVersion(@PathVariable String skillCode,
                                                         @PathVariable int version,
                                                         @RequestBody(required = false) AgentSkillWorkflowCommand request) {
        requireLegacySkillWriteOverride(request == null ? null : request.getMetadata(), "LEGACY_SKILL_VERSION_WRITE_BLOCKED: P3-L blocks legacy skill rollback writes. Use Dispatch Policy v2.");
        return skillRegistryService.rollbackToVersion(skillCode, version, request);
    }

    @GetMapping("/admin/agent-skills/{skillCode}/audit")
    public List<AgentSkillAuditEntry> listSkillAudit(@PathVariable String skillCode,
                                                     @RequestParam(defaultValue = "100") int limit) {
        return skillRegistryService.listAuditEntries(skillCode, limit);
    }

    @GetMapping("/admin/agent-skills/{skillCode}/versions/{version}/diff")
    public AgentSkillDiffResult diffSkillVersion(@PathVariable String skillCode,
                                                 @PathVariable int version,
                                                 @RequestParam(required = false) Integer baseVersion) {
        return skillRegistryService.diffVersion(skillCode, baseVersion, version);
    }

    @GetMapping("/admin/agent-skills/{skillCode}/versions/{version}/impact")
    public AgentSkillImpactAnalysisResult analyzeSkillVersionImpact(@PathVariable String skillCode,
                                                                    @PathVariable int version) {
        return skillRegistryService.analyzeImpact(skillCode, version);
    }

    @GetMapping("/admin/agent-skills/{skillCode}/approval-policy")
    public AgentSkillApprovalPolicy getSkillApprovalPolicy(@PathVariable String skillCode) {
        return skillRegistryService.getApprovalPolicy(skillCode);
    }

    @PutMapping("/admin/agent-skills/{skillCode}/approval-policy")
    public AgentSkillApprovalPolicy updateSkillApprovalPolicy(@PathVariable String skillCode,
                                                              @RequestBody(required = false) AgentSkillApprovalPolicy request,
                                                              @RequestParam(required = false) String operatorId) {
        return skillRegistryService.updateApprovalPolicy(skillCode, request, operatorId);
    }


    @GetMapping("/admin/agents/{agentId}/skills/drift")
    public AgentCapabilityDriftReport detectAgentSkillDrift(@PathVariable String agentId) {
        AgentProfile profile = agentGovernanceService.getProfile(agentId);
        List<AgentRuntimeCapabilityItem> runtimeItems = agentDirectoryService.findRuntimeCapabilityItems(agentId);
        return skillRegistryService.detectAgentDrift(profile, runtimeItems);
    }

    @GetMapping("/admin/agent-skills/drift")
    public AgentCapabilityDriftReport detectFleetSkillDrift(@RequestParam(required = false) AgentApprovalStatus status,
                                                            @RequestParam(defaultValue = "500") int limit) {
        List<AgentProfile> profiles = agentGovernanceService.searchProfiles(status, Math.max(1, limit));
        Map<String, List<AgentRuntimeCapabilityItem>> runtimeByAgent = profiles.stream()
                .filter(profile -> profile != null && profile.getAgentId() != null)
                .collect(java.util.stream.Collectors.toMap(AgentProfile::getAgentId, profile -> agentDirectoryService.findRuntimeCapabilityItems(profile.getAgentId()), (left, right) -> left, java.util.LinkedHashMap::new));
        return skillRegistryService.detectFleetDrift(profiles, runtimeByAgent);
    }

    @GetMapping("/admin/agent-skills/{skillCode}/deprecation-plan")
    public AgentSkillDeprecationPlan getSkillDeprecationPlan(@PathVariable String skillCode) {
        return skillRegistryService.getDeprecationPlan(skillCode);
    }

    @PutMapping("/admin/agent-skills/{skillCode}/deprecation-plan")
    public AgentSkillDeprecationPlan updateSkillDeprecationPlan(@PathVariable String skillCode,
                                                                @RequestBody(required = false) AgentSkillDeprecationCommand request) {
        return skillRegistryService.upsertDeprecationPlan(skillCode, request);
    }

    @PostMapping("/admin/agent-skills/{skillCode}/deprecation-plan/analyze")
    public AgentSkillDeprecationMigrationPlan analyzeSkillDeprecationMigrationPlan(@PathVariable String skillCode,
                                                                                  @RequestParam(defaultValue = "500") int limit) {
        List<AgentProfile> profiles = agentGovernanceService.searchProfiles(null, Math.max(1, limit));
        Map<String, List<AgentRuntimeCapabilityItem>> runtimeByAgent = profiles.stream()
                .filter(profile -> profile != null && profile.getAgentId() != null)
                .collect(java.util.stream.Collectors.toMap(AgentProfile::getAgentId, profile -> agentDirectoryService.findRuntimeCapabilityItems(profile.getAgentId()), (left, right) -> left, java.util.LinkedHashMap::new));
        return skillRegistryService.analyzeDeprecationMigrationPlan(skillCode, profiles, runtimeByAgent);
    }



    @GetMapping("/admin/agent-skills/{skillCode}/dependency-graph")
    public AgentSkillDependencyGraph getSkillDependencyGraph(@PathVariable String skillCode,
                                                             @RequestParam(defaultValue = "2") int depth) {
        return skillRegistryService.dependencyGraph(skillCode, depth);
    }

    @PutMapping("/admin/agent-skills/{skillCode}/dependencies")
    public List<AgentSkillDependencyEdge> replaceSkillDependencies(@PathVariable String skillCode,
                                                                   @RequestBody(required = false) AgentSkillDependencyCommand request) {
        return skillRegistryService.replaceDependencyEdges(skillCode, request);
    }

    @PostMapping("/admin/agent-skills/drift/remediation-proposals")
    public AgentSkillRemediationProposal proposeFleetSkillRemediation(@RequestParam(defaultValue = "500") int limit) {
        List<AgentProfile> profiles = agentGovernanceService.searchProfiles(null, Math.max(1, limit));
        Map<String, List<AgentRuntimeCapabilityItem>> runtimeByAgent = profiles.stream()
                .filter(profile -> profile != null && profile.getAgentId() != null)
                .collect(java.util.stream.Collectors.toMap(AgentProfile::getAgentId, profile -> agentDirectoryService.findRuntimeCapabilityItems(profile.getAgentId()), (left, right) -> left, java.util.LinkedHashMap::new));
        return skillRegistryService.proposeFleetRemediation(profiles, runtimeByAgent);
    }

    @PostMapping("/admin/agent-skills/drift/policy-evaluate")
    public SkillDriftPolicyEvaluationResponse evaluateSkillDriftPolicy(@RequestBody(required = false) SkillDriftPolicyEvaluationRequest request) {
        SkillDriftPolicyEvaluationRequest body = request == null ? new SkillDriftPolicyEvaluationRequest(null, 500, false) : request;
        int limit = body.limit() == null ? 500 : Math.max(1, Math.min(5000, body.limit()));
        List<AgentProfile> profiles = agentGovernanceService.searchProfiles(null, limit);
        Map<String, List<AgentRuntimeCapabilityItem>> runtimeByAgent = profiles.stream()
                .filter(profile -> profile != null && profile.getAgentId() != null)
                .collect(java.util.stream.Collectors.toMap(AgentProfile::getAgentId, profile -> agentDirectoryService.findRuntimeCapabilityItems(profile.getAgentId()), (left, right) -> left, java.util.LinkedHashMap::new));
        AgentCapabilityDriftReport report = skillRegistryService.detectFleetDrift(profiles, runtimeByAgent);
        List<SkillDriftPolicyAction> actions = new ArrayList<>();
        int reviewEvents = 0;
        int quarantineRecommendations = 0;
        int dispatchDegradeRecommendations = 0;
        for (AgentCapabilityDriftItem item : report.getItems()) {
            String enforcement = recommendedDriftEnforcement(item);
            if (enforcement == null) {
                continue;
            }
            if ("QUARANTINE_RECOMMENDED".equals(enforcement)) quarantineRecommendations++;
            if ("DISPATCH_DEGRADED".equals(enforcement)) dispatchDegradeRecommendations++;
            if (body.persistEvents()) {
                saveSkillDriftPolicyEvent(item, enforcement, body.operatorId());
                reviewEvents++;
            }
            actions.add(new SkillDriftPolicyAction(item.getAgentId(), item.getSkillCode(), item.getDriftType(), item.getSeverity(), enforcement, item.getSuggestedAction()));
        }
        return new SkillDriftPolicyEvaluationResponse(
                report.getScannedAgents(),
                report.getDriftCount(),
                report.getHighSeverityCount(),
                actions.size(),
                quarantineRecommendations,
                dispatchDegradeRecommendations,
                reviewEvents,
                actions,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @PostMapping("/admin/agents/{agentId}/skills/remediation-proposal")
    public AgentSkillRemediationProposal proposeAgentSkillRemediation(@PathVariable String agentId) {
        AgentProfile profile = agentGovernanceService.getProfile(agentId);
        List<AgentRuntimeCapabilityItem> runtimeItems = agentDirectoryService.findRuntimeCapabilityItems(agentId);
        return skillRegistryService.proposeAgentRemediation(profile, runtimeItems);
    }

    @PostMapping("/admin/dispatch-contracts/resolve")
    public TaskDispatchContractResolveResult resolveDispatchContract(@RequestBody(required = false) TaskDispatchContractResolveRequest request) {
        return dispatchContractResolverService.resolve(request);
    }

    @GetMapping("/admin/agents/{agentId}/skills/approved")
    public List<AgentApprovedSkill> getApprovedSkills(@PathVariable String agentId,
                                                      @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return skillRegistryService.getApprovedSkills(agentId, enabledOnly);
    }

    @PutMapping("/admin/agents/{agentId}/skills/approved")
    public AgentApprovedSkillSyncResult replaceApprovedSkills(@PathVariable String agentId,
                                                              @RequestBody(required = false) AgentApprovedSkillSyncCommand request) {
        AgentProfile profile = agentGovernanceService.getProfile(agentId);
        AgentApprovedSkillSyncCommand body = request == null ? new AgentApprovedSkillSyncCommand() : request;
        skillRegistryService.replaceApprovedSkills(agentId, body, profile);
        AgentApprovedSkillSyncResult result = skillRegistryService.buildSyncResult(agentId, profile, body.getSkillCodes(), false, "Approved skills replaced from Admin UI/API.");
        if (Boolean.TRUE.equals(body.getSyncProfileCapabilities())) {
            syncProfileCapabilities(agentId, result, body);
            AgentProfile updated = agentGovernanceService.getProfile(agentId);
            result = skillRegistryService.buildSyncResult(agentId, updated, body.getSkillCodes(), true, "Approved skills and profile capabilities synchronized.");
        }
        return result;
    }

    @PostMapping("/admin/agents/{agentId}/skills/sync-approved-capabilities")
    public AgentApprovedSkillSyncResult syncApprovedSkillsAndCapabilities(@PathVariable String agentId,
                                                                          @RequestBody(required = false) AgentApprovedSkillSyncCommand request) {
        AgentProfile profile = agentGovernanceService.getProfile(agentId);
        AgentApprovedSkillSyncCommand body = request == null ? new AgentApprovedSkillSyncCommand() : request;
        AgentApprovedSkillSyncResult preview = skillRegistryService.buildSyncResult(agentId, profile, body.getSkillCodes(), false, "Preview approved skill/profile capability union before synchronization.");
        body.setSkillCodes(preview.getApprovedSkillCodes());
        skillRegistryService.replaceApprovedSkills(agentId, body, profile);
        syncProfileCapabilities(agentId, preview, body);
        AgentProfile updated = agentGovernanceService.getProfile(agentId);
        return skillRegistryService.buildSyncResult(agentId, updated, preview.getApprovedSkillCodes(), true, "Approved skill table and governance capabilities synchronized bidirectionally.");
    }

    @PostMapping("/admin/agents/{agentId}/skills/evaluate")
    public AgentSkillEvaluationResult evaluateAgentSkill(@PathVariable String agentId,
                                                         @RequestBody(required = false) AgentSkillEvaluationRequest request) {
        AgentProfile profile = agentGovernanceService.getProfile(agentId);
        List<AgentRuntimeCapabilityItem> runtimeItems = agentDirectoryService.findRuntimeCapabilityItems(agentId);
        return skillRegistryService.evaluate(profile, runtimeItems, request);
    }

    private String recommendedDriftEnforcement(AgentCapabilityDriftItem item) {
        if (item == null || item.getDriftType() == null) {
            return null;
        }
        String driftType = item.getDriftType();
        String severity = item.getSeverity() == null ? "" : item.getSeverity();
        if ("HIGH".equalsIgnoreCase(severity)
                || driftType.contains("UNKNOWN")
                || driftType.contains("DISABLED")
                || driftType.contains("DEPRECATED")) {
            return "QUARANTINE_RECOMMENDED";
        }
        if ("REPORTED_NOT_APPROVED".equals(driftType) || "APPROVED_NOT_REPORTED".equals(driftType) || "NO_SKILL_SIGNAL".equals(driftType)) {
            return "DISPATCH_DEGRADED";
        }
        return "REVIEW_REQUIRED";
    }

    private void saveSkillDriftPolicyEvent(AgentCapabilityDriftItem item, String enforcement, String operatorId) {
        AgentSecurityEvent event = new AgentSecurityEvent();
        event.setAgentId(item.getAgentId());
        event.setClaimedAgentId(item.getAgentId());
        event.setEventType(switch (enforcement) {
            case "QUARANTINE_RECOMMENDED" -> AgentSecurityEventType.SKILL_DRIFT_QUARANTINE_RECOMMENDED;
            case "DISPATCH_DEGRADED" -> AgentSecurityEventType.SKILL_DRIFT_DISPATCH_DEGRADED;
            default -> AgentSecurityEventType.SKILL_DRIFT_REVIEW_REQUIRED;
        });
        event.setReason(item.getDetail());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("operatorId", operatorId == null || operatorId.isBlank() ? "system" : operatorId);
        metadata.put("skillCode", item.getSkillCode());
        metadata.put("driftType", item.getDriftType());
        metadata.put("severity", item.getSeverity());
        metadata.put("suggestedAction", item.getSuggestedAction());
        metadata.put("recommendedEnforcement", enforcement);
        metadata.put("replacementSkillCodes", item.getReplacementSkillCodes());
        event.setMetadata(metadata);
        agentGovernanceService.saveSecurityEvent(event);
    }

    private void syncProfileCapabilities(String agentId, AgentApprovedSkillSyncResult result, AgentApprovedSkillSyncCommand command) {
        AgentProfileUpdateCommand update = new AgentProfileUpdateCommand();
        update.setCapabilities(result.getProfileCapabilityCodes());
        update.setOperatorId(command == null || command.getOperatorId() == null ? "system" : command.getOperatorId());
        update.setReason(command == null || command.getReason() == null ? "Synchronize Agent approved skills with governance capabilities." : command.getReason());
        agentGovernanceService.updateProfile(agentId, update);
    }


    public record SkillDriftPolicyEvaluationRequest(String operatorId, Integer limit, boolean persistEvents) {}
    public record SkillDriftPolicyAction(String agentId, String skillCode, String driftType, String severity, String recommendedEnforcement, String suggestedAction) {}
    public record SkillDriftPolicyEvaluationResponse(
            int scannedAgents,
            int driftCount,
            int highSeverityCount,
            int actionCount,
            int quarantineRecommendations,
            int dispatchDegradeRecommendations,
            int persistedEvents,
            List<SkillDriftPolicyAction> actions,
            OffsetDateTime evaluatedAt
    ) {}

}
