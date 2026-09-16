package com.opensocket.aievent.core.a2a.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Framework-neutral integrity and binding rules for immutable A2A Handoff snapshots. */
public final class HandoffSnapshotIntegrityGuard {
    /** Phase 2E canonical aggregate schema. Schema 1 remains readable for upgrade compatibility. */
    public static final int SNAPSHOT_SCHEMA_VERSION = 2;

    /** Compatibility hash used by schema version 1 snapshots. */
    public String contentHash(String tenantId, String rootTaskId, String sourceTaskId,
                              String targetTaskId, String targetDomainId, String sourceAssignmentId,
                              String policyId, long policyVersion, int snapshotVersion,
                              Object summary, Object structuredContext, Object comments,
                              Object attachments, Object redacted, Object omitted) {
        return contentHash(1, tenantId, rootTaskId, sourceTaskId, targetTaskId,
                sourceAssignmentId, null, targetDomainId, null, policyId, policyVersion,
                snapshotVersion, summary, structuredContext, comments, attachments, redacted, omitted);
    }

    public String contentHash(int schemaVersion, String tenantId, String rootTaskId,
                              String sourceTaskId, String targetTaskId, String sourceAgentId,
                              String targetAgentId, String targetDomainId, String targetBindingHash,
                              String policyId, long policyVersion, int snapshotVersion,
                              Object summary, Object structuredContext, Object comments,
                              Object attachments, Object redacted, Object omitted) {
        if (schemaVersion <= 1) {
            return sha256(canonical(1, tenantId, rootTaskId, sourceTaskId, targetTaskId,
                    targetDomainId, sourceAgentId, policyId, policyVersion, snapshotVersion,
                    summary, structuredContext, comments, attachments, redacted, omitted));
        }
        return sha256(canonical(schemaVersion, tenantId, rootTaskId, sourceTaskId, targetTaskId,
                sourceAgentId, targetAgentId, targetDomainId, targetBindingHash, policyId,
                policyVersion, snapshotVersion, summary, structuredContext, comments,
                attachments, redacted, omitted));
    }

    public String aggregateId(String tenantId, String sourceTaskId, String targetTaskId, String policyId) {
        return "hagg-" + sha256(canonical(tenantId, sourceTaskId, targetTaskId, policyId)).substring(0, 32);
    }

    public String targetBindingHash(String tenantId, String targetTaskId,
                                    String targetAgentId, String targetDomainId) {
        return sha256(canonical(tenantId, targetTaskId, normalize(targetAgentId),
                normalize(targetDomainId)));
    }

    public String approvalEvidenceHash(Object approvals) {
        return sha256(canonical(approvals));
    }

    public void verify(String expectedTenantId, String expectedTargetTaskId, String expectedTargetDomainId,
                       OffsetDateTime expiresAt, String status, String expectedHash, String actualHash) {
        require(expectedTenantId, "tenantId");
        require(expectedTargetTaskId, "targetTaskId");
        require(expectedTargetDomainId, "targetDomainId");
        if (!"APPROVED".equals(status)) throw new IllegalStateException("AGENT_CONTEXT_SNAPSHOT_NOT_APPROVED");
        if (expiresAt != null && !expiresAt.isAfter(OffsetDateTime.now())) {
            throw new IllegalStateException("HANDOFF_CONTEXT_SNAPSHOT_EXPIRED");
        }
        if (!constantEquals(expectedHash, actualHash)) {
            throw new IllegalStateException("HANDOFF_CONTEXT_CONTENT_HASH_CONFLICT");
        }
    }

    public void verifyBinding(String expected, String actual, String reasonCode) {
        if (!Objects.equals(require(expected, "expectedBinding"), require(actual, "actualBinding"))) {
            throw new IllegalStateException(reasonCode);
        }
    }

    public boolean constantEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private String canonical(Object... values) {
        return java.util.Arrays.stream(values).map(this::canon).collect(Collectors.joining("|"));
    }

    private String canon(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) return map.entrySet().stream()
                .sorted(Comparator.comparing(e -> String.valueOf(e.getKey())))
                .map(e -> String.valueOf(e.getKey()) + "=" + canon(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
        if (value instanceof Collection<?> collection) return collection.stream().map(this::canon).sorted()
                .collect(Collectors.joining(",", "[", "]"));
        return String.valueOf(value);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash Handoff Snapshot", exception);
        }
    }

    private String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "UNASSIGNED" : value.trim();
    }
}
