package com.opensocket.aievent.core.a2a;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Deterministic identity fingerprint. Raw fencing material is never stored here. */
public final class A2ACancellationFingerprint {
    private A2ACancellationFingerprint() {}
    public static String of(A2ACancellationRecord value) {
        String canonical=String.join("|", safe(value.getTenantId()), safe(value.getRequestId()),
                safe(value.getChildTaskId()), safe(value.getAssignmentId()),
                safe(value.getExecutionAttemptId()), String.valueOf(value.getAttemptNo()),
                safe(value.getDispatchRequestId()), safe(value.getRevokedFencingTokenHash()),
                safe(value.getActiveFencingTokenHash()), safe(value.getIdempotencyKey()));
        try { return "can-"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("Unable to fingerprint cancellation",ex); }
    }
    private static String safe(Object value){return value==null?"":String.valueOf(value);}
}
