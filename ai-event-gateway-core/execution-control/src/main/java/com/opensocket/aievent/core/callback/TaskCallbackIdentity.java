package com.opensocket.aievent.core.callback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic callback identity and replay-protection boundary.
 *
 * <p>The callback service remains the transaction and state-transition authority;
 * this helper only canonicalizes request identity, fingerprints secrets and
 * evaluates callback-id replay mismatch.</p>
 */
final class TaskCallbackIdentity {
    private final TaskCallbackProperties properties;

    TaskCallbackIdentity(TaskCallbackProperties properties) {
        this.properties = properties;
    }

    String generatedCallbackId(TaskCallbackType type, TaskCallbackRequest request) {
        String raw = String.join("|",
                type.name(),
                safe(request.getTaskId()),
                safe(request.getDispatchRequestId()),
                safe(request.getAssignmentId()),
                safe(request.getAgentId()),
                safe(request.getOwnerGatewayNodeId()),
                safe(request.getAgentSessionId()),
                safe(request.getAttemptNo()),
                safe(request.getFencingToken()),
                safe(request.getProgressPercent()),
                safe(request.getResultStatus()),
                safe(request.getErrorCode()),
                safe(request.getErrorMessage()),
                safe(request.getMessage()));
        return sha256("cb-", raw);
    }

    String idempotencyKey(TaskCallbackType type, TaskCallbackRequest request) {
        String raw = String.join("|",
                "TASK_CALLBACK_IDEMPOTENCY_V2",
                type == null ? "" : type.name(),
                safe(request.getTaskId()),
                safe(request.getDispatchRequestId()),
                safe(request.getAssignmentId()),
                safe(request.getAgentId()),
                safe(request.getOwnerGatewayNodeId()),
                safe(request.getAgentSessionId()),
                safe(request.getAttemptNo()),
                safe(request.getCallbackId()));
        return sha256("idk-", raw);
    }

    String callbackFingerprint(TaskCallbackType type, TaskCallbackRequest request) {
        String raw = String.join("|",
                "TASK_CALLBACK_FINGERPRINT_V2",
                type == null ? "" : type.name(),
                safe(request.getTaskId()),
                safe(request.getDispatchRequestId()),
                safe(request.getAssignmentId()),
                safe(request.getAgentId()),
                safe(request.getOwnerGatewayNodeId()),
                safe(request.getAgentSessionId()),
                safe(request.getAttemptNo()),
                secretFingerprint(request.getDispatchToken()),
                secretFingerprint(request.getFencingToken()),
                safe(request.getProgressPercent()),
                safe(request.getResultStatus()),
                safe(request.getErrorCode()),
                safe(request.getErrorMessage()),
                safe(request.getMessage()),
                canonical(request.getPayload()));
        return sha256("cbf-", raw);
    }

    boolean replayMismatch(TaskCallbackRecord current, TaskCallbackRecord previous) {
        if (!properties.isReplayProtectionEnabled() || !properties.isRejectCallbackIdReplayMismatch()) {
            return false;
        }
        String currentFingerprint = current == null ? null : current.getCallbackFingerprint();
        String previousFingerprint = previous == null ? null : previous.getCallbackFingerprint();
        if (currentFingerprint == null || currentFingerprint.isBlank()
                || previousFingerprint == null || previousFingerprint.isBlank()) {
            return false;
        }
        return !Objects.equals(currentFingerprint, previousFingerprint);
    }

    String secretFingerprint(String secret) {
        return secret == null || secret.isBlank() ? "" : sha256("sec-", secret);
    }

    String payloadFingerprint(Object payload) {
        return sha256("pay-", canonical(payload));
    }

    private String sha256(String prefix, String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return prefix + HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return prefix + Math.abs(raw.hashCode());
        }
    }

    private String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted((a, b) -> String.valueOf(a.getKey()).compareTo(String.valueOf(b.getKey())))
                    .map(entry -> String.valueOf(entry.getKey()) + "=" + canonical(entry.getValue()))
                    .toList()
                    .toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) builder.append(',');
                builder.append(canonical(item));
                first = false;
            }
            return builder.append(']').toString();
        }
        return String.valueOf(value);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
