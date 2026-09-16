package com.opensocket.aievent.core.integration.issue.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runtime policy for the v24 canonical Task RESULT -> Issue Projection authority. */
@Component
@ConfigurationProperties(prefix = "issue-orchestration")
public class IssuePolicyOrchestrationProperties {
    private boolean automaticMaterializationEnabled = true;
    private boolean optionalFailedTaskRequiresIssue = true;
    private String policyId = "task-issue-sync-policy-v1";
    private int policyVersion = 1;

    public boolean isAutomaticMaterializationEnabled() { return automaticMaterializationEnabled; }
    public void setAutomaticMaterializationEnabled(boolean value) { this.automaticMaterializationEnabled = value; }
    public boolean isOptionalFailedTaskRequiresIssue() { return optionalFailedTaskRequiresIssue; }
    public void setOptionalFailedTaskRequiresIssue(boolean value) { this.optionalFailedTaskRequiresIssue = value; }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(String value) { this.policyId = value; }
    public int getPolicyVersion() { return Math.max(1, policyVersion); }
    public void setPolicyVersion(int value) { this.policyVersion = Math.max(1, value); }
}
