package com.opensocket.aievent.core.a2a.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.opensocket.aievent.core.a2a.A2AResultSubmission;

/** Deterministic fingerprints used to distinguish replay from conflicting duplicate. */
public final class A2AResultFingerprint {
    private A2AResultFingerprint() {}

    public static String result(A2AResultSubmission value) {
        return hash("res-", canonical(value.resultStatus(), value.payloadHash(), value.assignmentId(),
                value.executionAttemptId(), value.attemptNo(), value.dispatchRequestId(),
                value.callbackInboxId(), value.completedByAgentId(), value.resultSchemaVersion()));
    }

    public static String evidence(A2AResultSubmission value) {
        return hash("evh-", canonical(result(value), value.dispatchTokenHash(), value.fencingTokenHash(),
                value.agentSessionId(), value.evidenceRefs()));
    }

    private static String canonical(Object... values) {
        return java.util.Arrays.stream(values).map(v -> v == null ? "" : String.valueOf(v).trim())
                .reduce((a,b) -> a + "\u001f" + b).orElse("");
    }

    private static String hash(String prefix, String value) {
        try {
            return prefix + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
