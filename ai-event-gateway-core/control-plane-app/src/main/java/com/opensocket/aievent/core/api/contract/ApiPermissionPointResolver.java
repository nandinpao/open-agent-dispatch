package com.opensocket.aievent.core.api.contract;

import org.springframework.stereotype.Component;

@Component
public class ApiPermissionPointResolver {
    public String resolve(String method, String path) {
        String normalizedMethod = method == null ? "" : method.toUpperCase();
        String normalizedPath = path == null ? "" : path;

        if (normalizedPath.contains("/a2a-requests") && normalizedPath.endsWith("/approve")) return "a2a.approve";
        if (normalizedPath.contains("/a2a-requests") && normalizedPath.endsWith("/reject")) return "a2a.reject";
        if (normalizedPath.contains("/a2a-requests") && normalizedPath.endsWith("/cancel")) return "a2a.cancel";
        if (normalizedPath.contains("/a2a-policies")) return "a2a.manage_policy";
        if (normalizedPath.contains("/a2a-requests")) return "a2a.request";

        if (normalizedPath.contains("/rotate-credential") || normalizedPath.contains("/rotations/")) return "integration.secret.rotate";
        if (normalizedPath.contains("/phase3-release-readiness")) return "integration.phase3_release.read";
        if (normalizedPath.contains("/security-overrides")) return "security.override";
        if (normalizedPath.contains("/project-mappings")) {
            return "GET".equals(normalizedMethod)
                    ? "integration.project_mapping.read"
                    : "integration.project_mapping.manage";
        }
        if (normalizedPath.contains("/webhooks/")) return "integration.webhook.receive";
        if (normalizedPath.contains("/external-change-governance/policies")) {
            return "GET".equals(normalizedMethod)
                    ? "integration.external_change_policy.read"
                    : "integration.external_change_policy.manage";
        }
        if (normalizedPath.contains("/external-change-governance/comment-sync")) return "integration.external_comment.sync";
        if (normalizedPath.contains("/external-change-governance/relation-sync")) return "integration.external_relation.sync";
        if (normalizedPath.contains("/external-change-governance/candidates") && normalizedPath.endsWith("/execute")) return "integration.provider_action.execute";
        if (normalizedPath.contains("/external-change-governance/candidates")) return "integration.provider_action.review";
        if (normalizedPath.contains("/relay-governance/compensations") || normalizedPath.contains("/compensations")) return "integration.relay.compensate";
        if (normalizedPath.contains("/relay-governance/edges") && (normalizedPath.endsWith("/retry") || normalizedPath.endsWith("/execute"))) return "integration.relay.retry";
        if (normalizedPath.contains("/relay-governance")) return "GET".equals(normalizedMethod) ? "integration.relay.read" : "integration.relay.manage";
        if (normalizedPath.contains("/projection-recovery/cases") && normalizedPath.endsWith("/repair")) return "integration.projection_recovery.repair";
        if (normalizedPath.contains("/projection-recovery/reconciliation-runs")) return "GET".equals(normalizedMethod) ? "integration.projection_recovery.read" : "integration.projection_recovery.reconcile";
        if (normalizedPath.contains("/projection-recovery")) return "GET".equals(normalizedMethod) ? "integration.projection_recovery.read" : "integration.projection_recovery.manage";
        if (normalizedPath.contains("/integration-sync/conflicts")) return "integration.issue.resolve_conflict";
        if (normalizedPath.contains("/dead-letters") || normalizedPath.endsWith("/retry")) return "integration.issue.retry";
        if (normalizedPath.contains("/task-issues") && normalizedPath.contains("/relationships")) return "integration.issue.relate";
        if (normalizedPath.contains("/task-issues") && normalizedPath.contains("/comments")) return "integration.issue.comment";
        if (normalizedPath.contains("/issues")) return "integration.issue.create";
        if (normalizedPath.contains("/integrations")) return "integration.connection.manage";

        if (normalizedPath.contains("/handoff-context")) return "handoff.manage";
        if (normalizedPath.contains("/relationships")) {
            return "DELETE".equals(normalizedMethod) ? "task.remove_reference" : "task.add_reference";
        }
        if (normalizedPath.contains("/participants")) return "task.update";
        if (normalizedPath.contains("/transitions") || normalizedPath.endsWith("/resolve")) return "task.resolve";
        if (normalizedPath.contains("/cancel")) return "task.cancel";
        if (normalizedPath.contains("/reassign")) return "task.reassign";
        if (normalizedPath.contains("/tasks") && "POST".equals(normalizedMethod)) return "task.create";
        if (normalizedPath.contains("/tasks")) return "task.update";

        if (normalizedPath.contains("/agents") || normalizedPath.contains("/agent-pools")) return "agent.manage";
        if (normalizedPath.contains("/source-systems")) return "source_system.manage";
        if (normalizedPath.contains("/dispatch")) return "dispatch.manage";
        if (normalizedPath.contains("/governance")) return "audit.read";
        return "system.manage";
    }
}
