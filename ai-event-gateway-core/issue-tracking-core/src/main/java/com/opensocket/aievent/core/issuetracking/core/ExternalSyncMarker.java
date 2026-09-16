package com.opensocket.aievent.core.issuetracking.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Deterministic, non-secret source marker used to stop comment/link reflection loops. */
public final class ExternalSyncMarker {
    public static final String PREFIX = "od-sync:v1:";

    private ExternalSyncMarker() { }

    public static String create(String tenantId, String connectionId, String resourceType, String sourceId) {
        return PREFIX + sha(required(tenantId) + "\n"
                + required(connectionId) + "\n"
                + required(resourceType) + "\n"
                + required(sourceId));
    }

    public static boolean isOpenDispatchMarker(String value) {
        return value != null && value.startsWith(PREFIX) && value.length() == PREFIX.length() + 64;
    }

    public static String bodyMarker(String marker) {
        return "<!-- " + required(marker) + " -->";
    }

    public static String extract(String body) {
        if (body == null) return "";
        int start = body.indexOf(PREFIX);
        if (start < 0) return "";
        int end = Math.min(body.length(), start + PREFIX.length() + 64);
        String candidate = body.substring(start, end);
        return isOpenDispatchMarker(candidate) ? candidate : "";
    }

    private static String sha(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("marker input is required");
        return value.trim();
    }
}
