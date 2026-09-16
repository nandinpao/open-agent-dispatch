package com.opensocket.aievent.core.issuetracking.contract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** Unique identity of a Projection aggregate. */
public record ProjectionAggregateKey(
        String tenantId,
        String taskId,
        String connectionId,
        String projectMappingId,
        ProjectionPurpose projectionPurpose) {

    public ProjectionAggregateKey {
        tenantId = requireText(tenantId, "tenantId");
        taskId = requireText(taskId, "taskId");
        connectionId = requireText(connectionId, "connectionId");
        projectMappingId = requireText(projectMappingId, "projectMappingId");
        projectionPurpose = Objects.requireNonNull(projectionPurpose, "projectionPurpose is required");
    }

    public String canonicalValue() {
        return String.join("|", tenantId, taskId, connectionId, projectMappingId, projectionPurpose.name());
    }

    public String stableHash() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalValue().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash Projection aggregate key", exception);
        }
    }

    public String stableProjectionId() {
        return "issue-projection-" + stableHash().substring(0, 32);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }
}
