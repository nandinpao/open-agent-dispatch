package com.opensocket.aievent.core.capability.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic execution-scoped key builder for canonical delegated/Plan child Tasks. */
final class CanonicalChildTaskKey {
    private static final int TASK_KEY_MAX_LENGTH = 160;

    private CanonicalChildTaskKey() {}

    static String create(boolean delegation, String runId, String stepId, int attemptNo) {
        if (delegation) {
            return boundedKey("CAP-", runId, TASK_KEY_MAX_LENGTH);
        }
        String full = "PLAN-" + runId + "-" + stepId + "-" + attemptNo;
        if (full.length() <= TASK_KEY_MAX_LENGTH) return full;
        String digest = sha256(runId + "\n" + stepId + "\n" + attemptNo).substring(0, 24);
        String compactRun = compact(runId, 72);
        String compactStep = compact(stepId, 44);
        String candidate = "PLAN-" + compactRun + "-" + compactStep + "-" + attemptNo + "-" + digest;
        if (candidate.length() <= TASK_KEY_MAX_LENGTH) return candidate;
        return "PLAN-" + digest + "-" + attemptNo;
    }

    private static String boundedKey(String prefix, String identity, int maxLength) {
        String full = prefix + identity;
        if (full.length() <= maxLength) return full;
        String digest = sha256(identity).substring(0, 32);
        int readable = Math.max(1, maxLength - prefix.length() - digest.length() - 1);
        return prefix + identity.substring(0, Math.min(readable, identity.length())) + "-" + digest;
    }

    private static String compact(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        String digest = sha256(value).substring(0, 16);
        int readable = Math.max(1, maxLength - digest.length() - 1);
        return value.substring(0, readable) + "-" + digest;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
