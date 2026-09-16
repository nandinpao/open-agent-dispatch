package com.opensocket.aievent.core.resourceaccess.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceFieldPolicySelectorTest {
    @Test
    void exactRuleWinsOverPermissiveWildcardRegardlessOfAdministrativePriority() {
        VisibilityFieldRule wildcard = new VisibilityFieldRule(
                "wildcard", "*", VisibilityLevel.METADATA, SensitivityLevel.INTERNAL,
                MaskingMethod.NONE, true, true, 0, 1);
        VisibilityFieldRule prefix = new VisibilityFieldRule(
                "prefix", "attachment.*", VisibilityLevel.STANDARD, SensitivityLevel.CONFIDENTIAL,
                MaskingMethod.REDACT, false, false, 5, 1);
        VisibilityFieldRule exact = new VisibilityFieldRule(
                "exact", "attachment.content", VisibilityLevel.SENSITIVE, SensitivityLevel.RESTRICTED,
                MaskingMethod.REDACT, false, false, 100, 1);
        VisibilityPolicyRecord policy = new VisibilityPolicyRecord(
                "tenant-a", "policy", ResourceType.TASK_ATTACHMENT, "Attachment",
                VisibilityLevel.SENSITIVE, SensitivityLevel.RESTRICTED, VisibilityPolicyState.ACTIVE,
                List.of(wildcard, prefix, exact), 1, "creator", "approver",
                Instant.parse("2026-07-29T00:00:00Z"), Instant.parse("2026-07-29T00:00:00Z"));

        assertEquals("exact", ResourceFieldPolicySelector.select(policy, "attachment.content").orElseThrow().fieldRuleId());
        assertEquals("prefix", ResourceFieldPolicySelector.select(policy, "attachment.preview").orElseThrow().fieldRuleId());
        assertEquals("wildcard", ResourceFieldPolicySelector.select(policy, "task.title").orElseThrow().fieldRuleId());
    }
}
