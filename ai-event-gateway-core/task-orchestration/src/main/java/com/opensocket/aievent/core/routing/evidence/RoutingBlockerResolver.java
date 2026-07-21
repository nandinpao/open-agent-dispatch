package com.opensocket.aievent.core.routing.evidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.opensocket.aievent.core.routing.AgentCandidateScore;
import com.opensocket.aievent.core.routing.DispatchUserFacingError;
import com.opensocket.aievent.core.routing.DispatchUserFacingErrorCode;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.routing.RoutingDecisionStatus;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.routing.eligibility.CandidateFilterResult;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Resolves user-facing blockers, troubleshooting text, and observation blocking
 * reason codes for routing decisions.
 *
 * <p>Phase 3-6 moves no-candidate diagnostics out of RoutingDecisionService
 * without changing existing error codes, messages, actions, or technical
 * details.</p>
 */
public final class RoutingBlockerResolver {

    public DispatchUserFacingError noCandidateError(TaskRecord task, CandidateFilterResult candidatePool) {
        return DispatchUserFacingError.of(
                DispatchUserFacingErrorCode.DISPATCH_NO_AGENT_ONLINE,
                "HIGH",
                candidatePool != null && !blank(candidatePool.targetPoolId()) ? poolFirstNoCandidateMessage(candidatePool) : "目前沒有可評分的 Agent 可派工。",
                candidatePool != null && !blank(candidatePool.targetPoolId()) ? poolFirstNoCandidateAction(candidatePool) : "請先確認至少一個 Agent 已連線、Credential 為 ACTIVE，且沒有被停用或排除。",
                candidatePool != null && !blank(candidatePool.targetPoolId()) ? "runbooks/dispatch/pool-first-troubleshooting" : "runbooks/dispatch/no-agent-online",
                details(
                        "taskId", task == null ? null : task.getTaskId(),
                        "taskType", taskTypeCode(task),
                        "sourceSystem", task == null ? null : task.getSourceSystem(),
                        "targetPoolId", candidatePool == null ? null : candidatePool.targetPoolId(),
                        "targetPoolCode", candidatePool == null ? null : candidatePool.targetPoolCode(),
                        "poolMemberCount", candidatePool == null ? 0 : candidatePool.memberCount(),
                        "eligibleAgentCount", candidatePool == null ? 0 : candidatePool.eligibleAgentCount(),
                        "blockerCode", candidatePool == null ? null : candidatePool.poolBlockerCode(),
                        "poolBlocker", candidatePool == null ? null : candidatePool.poolBlockerCode()),
                details(
                        "includedCandidates", candidatePool == null ? 0 : candidatePool.included().size(),
                        "reservationExcluded", candidatePool == null ? Set.of() : candidatePool.reservationExcluded(),
                        "poisonExcluded", candidatePool == null ? Set.of() : candidatePool.poisonExcluded(),
                        "targetPoolId", candidatePool == null ? null : candidatePool.targetPoolId(),
                        "targetPoolCode", candidatePool == null ? null : candidatePool.targetPoolCode(),
                        "poolMemberCount", candidatePool == null ? 0 : candidatePool.memberCount(),
                        "eligibleAgentCount", candidatePool == null ? 0 : candidatePool.eligibleAgentCount(),
                        "blockerCode", candidatePool == null ? null : candidatePool.poolBlockerCode(),
                        "poolBlocker", candidatePool == null ? null : candidatePool.poolBlockerCode()));
    }

    public DispatchUserFacingError belowMinimumError(TaskRecord task,
                                                     AgentCandidateScore selected,
                                                     CandidateFilterResult candidatePool,
                                                     RoutingProperties properties) {
        Map<String, Object> breakdown = selected.scoreBreakdown();
        boolean backendBlocking = booleanBreakdown(breakdown, "backendEligibilityBlocking");
        List<String> requiredProfiles = stringListBreakdown(breakdown, "backendEligibilityRequiredProfiles");
        List<String> approvedProfiles = stringListBreakdown(breakdown, "backendEligibilityApprovedProfiles");
        if (backendBlocking && requiredProfiles.isEmpty()) {
            return profileNotConfiguredError(task);
        }
        if (backendBlocking) {
            String backendCode = firstBackendDispatchCode(selected);
            if (!blank(backendCode)) {
                return backendEligibilityError(task, selected, backendCode, requiredProfiles, approvedProfiles, candidatePool, properties);
            }
            if (containsAllNormalized(approvedProfiles, requiredProfiles)) {
                return DispatchUserFacingError.of(
                        DispatchUserFacingErrorCode.DISPATCH_AGENT_NOT_ASSIGNABLE,
                        "HIGH",
                        "候選 Agent 已具備必要 Profile，但目前 runtime 狀態不可接任務。",
                        "請確認 Agent runtime 已在線、未 draining/backoff，且仍有可用容量；修正後請從 Recovery Console 立即重試。",
                        "runbooks/dispatch/agent-not-assignable",
                        details("taskId", task == null ? null : task.getTaskId(), "selectedAgent", selected.agentId(), "requiredProfiles", requiredProfiles, "approvedProfiles", approvedProfiles),
                        technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool, properties));
            }
            return DispatchUserFacingError.of(
                    DispatchUserFacingErrorCode.DISPATCH_AGENT_PROFILE_MISSING,
                    "HIGH",
                    "目前沒有 Agent 取得此任務所需的後台派工資格。",
                    "請將 Profile " + requiredProfiles + " 指派並核准給可用 Agent；若該 Profile 需要認證，請先執行 Certification。",
                    "runbooks/dispatch/agent-profile-missing",
                    details("taskId", task == null ? null : task.getTaskId(), "requiredProfiles", requiredProfiles),
                    technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool, properties));
        }
        if (booleanBreakdown(breakdown, "runtimeCapacityFull")) {
            return DispatchUserFacingError.of(
                    DispatchUserFacingErrorCode.DISPATCH_AGENT_NO_CAPACITY,
                    "MEDIUM",
                    "候選 Agent 目前沒有足夠容量接新任務。",
                    "請等待目前任務完成、增加 Agent maxConcurrentTasks，或啟動更多 Agent。",
                    "runbooks/dispatch/agent-no-capacity",
                    details("taskId", task == null ? null : task.getTaskId(), "selectedAgent", selected.agentId()),
                    technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool, properties));
        }
        boolean runtimeAssignable = booleanBreakdown(breakdown, "runtimeAssignable");
        Number availabilityScore = numericBreakdown(breakdown, "availabilityScore");
        if (!runtimeAssignable || (availabilityScore != null && availabilityScore.intValue() <= 0)) {
            return DispatchUserFacingError.of(
                    DispatchUserFacingErrorCode.DISPATCH_AGENT_NOT_ASSIGNABLE,
                    "HIGH",
                    "候選 Agent 目前不可接任務。",
                    "請確認 Agent 已啟用、未被治理規則封鎖，未處於 draining/backoff/offline，且 runtime 狀態允許派工。",
                    "runbooks/dispatch/agent-not-assignable",
                    details("taskId", task == null ? null : task.getTaskId(), "selectedAgent", selected.agentId()),
                    technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool, properties));
        }
        Number slotScore = numericBreakdown(breakdown, "slotScore");
        Number loadScore = numericBreakdown(breakdown, "loadScore");
        if ((slotScore != null && slotScore.intValue() <= 0) || (loadScore != null && loadScore.intValue() <= 0)) {
            return DispatchUserFacingError.of(
                    DispatchUserFacingErrorCode.DISPATCH_AGENT_NO_CAPACITY,
                    "MEDIUM",
                    "候選 Agent 目前沒有足夠容量接新任務。",
                    "請等待目前任務完成、增加 Agent maxConcurrentTasks，或啟動更多 Agent。",
                    "runbooks/dispatch/agent-no-capacity",
                    details("taskId", task == null ? null : task.getTaskId(), "selectedAgent", selected.agentId()),
                    technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool, properties));
        }
        return DispatchUserFacingError.of(
                DispatchUserFacingErrorCode.DISPATCH_SCORE_BELOW_THRESHOLD,
                "MEDIUM",
                "目前最佳 Agent 的派工分數低於系統門檻。",
                "請檢查候選 Agent 的 runtime capacity、site policy 與健康狀態。",
                "runbooks/dispatch/score-below-threshold",
                details("taskId", task == null ? null : task.getTaskId(), "selectedAgent", selected.agentId()),
                technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool, properties));
    }

    public String observationBlockingReasonCode(RoutingDecisionRecord decision) {
        if (decision.getStatus() == RoutingDecisionStatus.SELECTED) return "none";
        if (decision.getUserFacingError() != null && decision.getUserFacingError().getCode() != null) {
            return normalizeObservationValue(decision.getUserFacingError().getCode().name());
        }
        return switch (decision.getStatus()) {
            case SUPPRESSED -> "assignment_disabled";
            case MANUAL_REVIEW_REQUIRED -> "manual_review_required";
            case NO_CANDIDATE -> "no_candidate";
            case SELECTED -> "none";
        };
    }

    private String poolFirstNoCandidateMessage(CandidateFilterResult candidatePool) {
        String blocker = normalize(candidatePool == null ? null : candidatePool.poolBlockerCode());
        if ("POOL_HAS_NO_ACTIVE_MEMBER".equals(blocker)) return "目標 Agent Pool 沒有啟用中的成員。";
        if ("POOL_AGENT_RUNTIME_NOT_FOUND".equals(blocker)) return "目標 Agent Pool 的成員尚未建立 runtime binding。";
        if ("POOL_AGENT_OFFLINE".equals(blocker)) return "目標 Agent Pool 的成員目前離線或心跳不可用。";
        if ("POOL_AGENT_CAPACITY_FULL".equals(blocker)) return "目標 Agent Pool 的成員容量已滿。";
        if ("POOL_AGENT_BACKOFF".equals(blocker)) return "目標 Agent Pool 的成員暫時被 backoff 排除。";
        if ("RULE_TARGET_POOL_NOT_FOUND".equals(blocker)) return "Flow Rule 指定的 target Pool 不存在或未啟用。";
        return "目標 Agent Pool 目前沒有可派工的 Agent。";
    }

    private String poolFirstNoCandidateAction(CandidateFilterResult candidatePool) {
        String blocker = normalize(candidatePool == null ? null : candidatePool.poolBlockerCode());
        if ("POOL_HAS_NO_ACTIVE_MEMBER".equals(blocker)) return "到 Agent Pool 管理頁加入至少一個已核准 Agent，或啟用既有 Pool member。";
        if ("POOL_AGENT_RUNTIME_NOT_FOUND".equals(blocker)) return "先完成 Agent setup/runtime binding，再把該 Agent 加入此 Pool。";
        if ("POOL_AGENT_OFFLINE".equals(blocker)) return "啟動 Pool 內 Agent runtime，確認 credential active、heartbeat healthy。";
        if ("POOL_AGENT_CAPACITY_FULL".equals(blocker)) return "等待 Pool 內 Agent 釋放容量、提高 capacity，或加入新的 Agent。";
        if ("POOL_AGENT_BACKOFF".equals(blocker)) return "檢查 Agent 近期失敗紀錄，清除 backoff 或改派其他 Pool member。";
        if ("RULE_TARGET_POOL_NOT_FOUND".equals(blocker)) return "修正 Flow Rule 的 target Pool，或改用 Source Flow default Pool。";
        return "請確認目標 Pool 內已有啟用成員，且成員 Agent 已核准、已連線並有可用容量。";
    }

    private DispatchUserFacingError profileNotConfiguredError(TaskRecord task) {
        String resolvedTaskType = taskTypeCode(task);
        String resolvedSourceSystem = task == null ? null : task.getSourceSystem();
        return DispatchUserFacingError.of(
                DispatchUserFacingErrorCode.DISPATCH_PROFILE_NOT_CONFIGURED,
                "HIGH",
                "此任務找不到 ACTIVE 的嚴格派工 Profile / Dispatch Flow coverage 契約。",
                "請確認 sourceSystem=" + display(resolvedSourceSystem)
                        + "、taskType=" + display(resolvedTaskType)
                        + " 已有 ACTIVE Dispatch Task Definition，且已有 ACTIVE 的一任務一 Dispatch Flow coverage / Dispatch Flow Agent Assignment；Agent 只核准 capability 或 legacy 多任務 Scope 仍不足以派工。",
                "runbooks/dispatch/profile-not-configured",
                details(
                        "taskId", task == null ? null : task.getTaskId(),
                        "taskType", resolvedTaskType,
                        "rawTaskType", taskTypeCode(task),
                        "sourceSystem", resolvedSourceSystem),
                details(
                        "requirementSource", null,
                        "requiredProfiles", List.of(),
                        "taskDefinitionIds", List.of(),
                        "requiredCapabilities", List.of(),
                        "requiredRuntimeFeatures", List.of()));
    }

    private DispatchUserFacingError backendEligibilityError(TaskRecord task,
                                                            AgentCandidateScore selected,
                                                            String backendCode,
                                                            List<String> requiredProfiles,
                                                            List<String> approvedProfiles,
                                                            CandidateFilterResult candidatePool,
                                                            RoutingProperties properties) {
        DispatchUserFacingErrorCode code = toUserFacingCode(backendCode);
        String message = switch (code) {
            case DISPATCH_NO_AGENT_ONLINE -> "候選 Agent 的後台 Profile 已命中，但 runtime 線上狀態未通過。";
            case DISPATCH_TASK_DEFINITION_NOT_FOUND -> "此任務沒有 ACTIVE Dispatch Task Definition 契約，Routing 不允許使用 legacy source/task fallback。";
            case DISPATCH_PROFILE_POLICY_MISSING -> "此 Dispatch Flow Agent Assignment 缺少 ACTIVE Policy Binding，不能以未治理 Profile 派工。";
            case DISPATCH_PROFILE_CAPABILITY_MISSING -> "此 Dispatch Flow Agent Assignment 缺少 ACTIVE Capability Binding，或 Agent 未具備所需能力。";
            case DISPATCH_AGENT_CAPABILITY_PENDING_APPROVAL -> "候選 Agent 的必要 Capability 仍在等待核准。";
            case DISPATCH_AGENT_CAPABILITY_REVOKED -> "候選 Agent 的必要 Capability 已被撤銷、暫停、拒絕或過期。";
            case DISPATCH_AGENT_NOT_ASSIGNABLE -> "候選 Agent 已具備必要 Profile，但目前 runtime 狀態不可接任務。";
            case DISPATCH_AGENT_NO_CAPACITY -> "候選 Agent 已具備必要 Profile，但目前沒有足夠容量接新任務。";
            case DISPATCH_RUNTIME_FEATURE_MISSING -> "候選 Agent 缺少必要 Runtime Feature 的治理紀錄。";
            case DISPATCH_RUNTIME_FEATURE_UNTRUSTED -> "候選 Agent 只回報了 Runtime Feature observation，但尚未被信任。";
            case DISPATCH_RUNTIME_FEATURE_REVOKED -> "候選 Agent 的必要 Runtime Feature trust 已被撤銷或暫停。";
            default -> "候選 Agent 未通過後台治理型 Routing Eligibility Contract。";
        };
        String nextAction = switch (code) {
            case DISPATCH_NO_AGENT_ONLINE -> "請啟動或重新連線 Agent runtime，確認 heartbeat 更新後再由 Recovery Console 立即重試。";
            case DISPATCH_TASK_DEFINITION_NOT_FOUND -> "請到 Dispatch Task Definitions 建立或啟用對應 sourceSystem/taskType，並確認 Profile 參照該契約。";
            case DISPATCH_PROFILE_POLICY_MISSING -> "請到 Dispatch Flow Agent Assignments → Policy Bindings 綁定同 Task Definition 下的 ACTIVE policy。";
            case DISPATCH_PROFILE_CAPABILITY_MISSING -> "請到 Dispatch Flow Agent Assignments → Required Capabilities 綁定 ACTIVE Capability，並到 Agent Detail 核准 Agent Capability。";
            case DISPATCH_AGENT_CAPABILITY_PENDING_APPROVAL -> "請到 Agent Detail → Capabilities 審核並 Approve 必要 Capability assignment。";
            case DISPATCH_AGENT_CAPABILITY_REVOKED -> "請重新 request/approve Capability，或改派具備 APPROVED Capability 的 Agent。";
            case DISPATCH_AGENT_NOT_ASSIGNABLE -> "請確認 Agent 已啟用、runtime 未 draining/backoff/offline，且狀態已正規化為 IDLE 或 BUSY_ACCEPTING。";
            case DISPATCH_AGENT_NO_CAPACITY -> "請等待目前任務完成、提高 maxConcurrentTasks，或啟動更多具備相同 Profile 的 Agent。";
            case DISPATCH_RUNTIME_FEATURE_MISSING -> "請到 Agent Detail → Runtime Features 建立 observation 後 Verify/Trust 必要 runtime feature。";
            case DISPATCH_RUNTIME_FEATURE_UNTRUSTED -> "請到 Agent Detail → Runtime Features 建立 observation 後 Verify/Trust 必要 runtime feature。";
            case DISPATCH_RUNTIME_FEATURE_REVOKED -> "請重新驗證 runtime feature，或改派具備 TRUSTED feature 的 Agent。";
            default -> "請依 Troubleshooting Wizard 第一個 FAILED step 修正治理契約。";
        };
        String runbook = switch (code) {
            case DISPATCH_NO_AGENT_ONLINE -> "runbooks/dispatch/no-agent-online";
            case DISPATCH_TASK_DEFINITION_NOT_FOUND -> "runbooks/dispatch/task-definition-contract";
            case DISPATCH_PROFILE_POLICY_MISSING -> "runbooks/dispatch/profile-policy-binding";
            case DISPATCH_PROFILE_CAPABILITY_MISSING, DISPATCH_AGENT_CAPABILITY_PENDING_APPROVAL, DISPATCH_AGENT_CAPABILITY_REVOKED -> "runbooks/dispatch/capability-contract";
            case DISPATCH_AGENT_NOT_ASSIGNABLE -> "runbooks/dispatch/agent-not-assignable";
            case DISPATCH_AGENT_NO_CAPACITY -> "runbooks/dispatch/agent-no-capacity";
            case DISPATCH_RUNTIME_FEATURE_MISSING, DISPATCH_RUNTIME_FEATURE_UNTRUSTED, DISPATCH_RUNTIME_FEATURE_REVOKED -> "runbooks/dispatch/runtime-feature-trust";
            default -> "runbooks/dispatch/routing-eligibility-contract";
        };
        return DispatchUserFacingError.of(
                code,
                "HIGH",
                message,
                nextAction,
                runbook,
                details("taskId", task == null ? null : task.getTaskId(), "selectedAgent", selected == null ? null : selected.agentId(), "requiredProfiles", requiredProfiles),
                technicalBelowMinimum(selected, requiredProfiles, approvedProfiles, candidatePool, properties));
    }

    private Map<String, Object> technicalBelowMinimum(AgentCandidateScore selected,
                                                       List<String> requiredProfiles,
                                                       List<String> approvedProfiles,
                                                       CandidateFilterResult candidatePool,
                                                       RoutingProperties properties) {
        return details(
                "selectedAgent", selected.agentId(),
                "selectedScore", selected.score(),
                "minimumScore", properties.getMinimumScore(),
                "requiredProfiles", requiredProfiles,
                "approvedProfiles", approvedProfiles,
                "scoring", selected.reason(),
                "scoreBreakdown", selected.scoreBreakdown(),
                "reservationExcluded", candidatePool == null ? Set.of() : candidatePool.reservationExcluded(),
                "poisonExcluded", candidatePool == null ? Set.of() : candidatePool.poisonExcluded());
    }

    private DispatchUserFacingErrorCode toUserFacingCode(String value) {
        if (blank(value)) {
            return DispatchUserFacingErrorCode.DISPATCH_SCORE_BELOW_THRESHOLD;
        }
        switch (value) {
            case "RUNTIME_ONLINE":
                return DispatchUserFacingErrorCode.DISPATCH_NO_AGENT_ONLINE;
            case "CAPACITY_AVAILABLE":
                return DispatchUserFacingErrorCode.DISPATCH_AGENT_NO_CAPACITY;
            case "NOT_DRAINING", "RUNTIME_BINDING_ACTIVE", "CREDENTIAL_VALID", "AGENT_APPROVED", "AGENT_RISK_STATUS", "EVALUATION_ERROR":
                return DispatchUserFacingErrorCode.DISPATCH_AGENT_NOT_ASSIGNABLE;
            default:
                break;
        }
        try {
            return DispatchUserFacingErrorCode.valueOf(value);
        } catch (Exception ex) {
            return DispatchUserFacingErrorCode.DISPATCH_SCORE_BELOW_THRESHOLD;
        }
    }

    private String firstBackendDispatchCode(AgentCandidateScore selected) {
        if (selected == null) return null;
        List<String> diagnostics = new ArrayList<>();
        if (selected.missingCapabilities() != null) diagnostics.addAll(selected.missingCapabilities());
        diagnostics.addAll(stringListBreakdown(selected.scoreBreakdown(), "missingCapabilities"));
        diagnostics.addAll(stringListBreakdown(selected.scoreBreakdown(), "missingCapabilities"));
        for (String item : diagnostics) {
            String code = extractDispatchCode(item);
            if (!blank(code)) return code;
            code = extractBackendEligibilityCode(item);
            if (!blank(code)) return code;
        }
        return null;
    }

    private String extractBackendEligibilityCode(String value) {
        if (blank(value)) return null;
        String marker = "backendEligibility:";
        int start = value.indexOf(marker);
        if (start < 0) return null;
        int codeStart = start + marker.length();
        int codeEnd = value.indexOf(':', codeStart);
        if (codeEnd < 0 || codeEnd <= codeStart) return null;
        return value.substring(codeStart, codeEnd);
    }

    private String extractDispatchCode(String value) {
        if (blank(value)) return null;
        int start = value.indexOf("DISPATCH_");
        if (start < 0) return null;
        int end = start;
        while (end < value.length()) {
            char ch = value.charAt(end);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_') {
                end++;
            } else {
                break;
            }
        }
        return value.substring(start, end);
    }

    private boolean containsAllNormalized(List<String> approvedProfiles, List<String> requiredProfiles) {
        if (requiredProfiles == null || requiredProfiles.isEmpty()) {
            return false;
        }
        Set<String> approved = approvedProfiles == null ? Set.of() : approvedProfiles.stream()
                .map(this::normalize)
                .filter(value -> !blank(value))
                .collect(Collectors.toSet());
        return requiredProfiles.stream()
                .map(this::normalize)
                .filter(value -> !blank(value))
                .allMatch(approved::contains);
    }

    private Number numericBreakdown(Map<String, Object> breakdown, String key) {
        Object value = breakdown == null ? null : breakdown.get(key);
        return value instanceof Number number ? number : null;
    }

    private boolean booleanBreakdown(Map<String, Object> breakdown, String key) {
        Object value = breakdown == null ? null : breakdown.get(key);
        return value instanceof Boolean bool && bool;
    }

    private List<String> stringListBreakdown(Map<String, Object> breakdown, String key) {
        Object value = breakdown == null ? null : breakdown.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
        }
        return List.of();
    }

    private Map<String, Object> details(Object... keyValues) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        if (keyValues == null) {
            return values;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (key != null) {
                values.put(String.valueOf(key), keyValues[i + 1]);
            }
        }
        return values;
    }

    private String taskTypeCode(TaskRecord task) {
        return task == null ? null : normalize(task.getEffectiveTaskTypeCode());
    }

    private String display(String value) {
        return blank(value) ? "UNKNOWN" : value;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeObservationValue(String value) {
        if (blank(value)) return "none";
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
