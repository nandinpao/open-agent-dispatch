package com.opensocket.aievent.core.action.executor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared adapter-executor redaction utilities for logs, audit records and operator-facing errors.
 *
 * Adapter payloads and third-party HTTP error bodies may contain API keys, bearer tokens, private
 * tokens or credentials echoed by a proxy/vendor. Persisted audit snapshots and execution errors must
 * therefore be safe-by-default before they are exposed through Admin UI or logs.
 */
public final class AdapterSecretRedactor {
    public static final String REDACTED = "[REDACTED]";
    private static final int DEFAULT_MAX_TEXT_LENGTH = 1200;

    private static final Pattern BEARER = Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s\",;{}]+(?:[^\",;{}\\s]*)");
    private static final Pattern PRIVATE_TOKEN = Pattern.compile("(?i)((?:private-token|x-redmine-api-key|x-api-key|api-key|access-token|refresh-token|client-secret|token|secret|password)\\s*[:=]\\s*)[^\\s\",;{}]+(?:[^\",;{}\\s]*)");
    private static final Pattern JSON_SECRET = Pattern.compile("(?i)(\\\"(?:privateToken|private_token|apiKey|api_key|token|accessToken|access_token|refreshToken|refresh_token|bearerToken|bearer_token|password|secret|clientSecret|client_secret|authorization)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")");
    private static final Pattern QUERY_SECRET = Pattern.compile("(?i)([?&](?:token|api_key|apikey|access_token|refresh_token|client_secret|password|private_token|key)=)[^&#\\s]+") ;
    private static final Pattern USERINFO_SECRET = Pattern.compile("(?i)(https?://)[^/@\\s:]+(?::[^/@\\s]*)?@");

    private AdapterSecretRedactor() {}

    public static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) return false;
        String normalized = key.replace("-", "").replace("_", "").replace(".", "").toLowerCase(Locale.ROOT);
        return normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("credential")
                || normalized.contains("apikey")
                || normalized.contains("privatekey")
                || normalized.contains("authorization")
                || normalized.contains("bearer")
                || normalized.contains("clientsecret");
    }

    public static Map<String, Object> redactMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, redactValue(key, value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Object redactValue(String key, Object value) {
        if (value == null) return null;
        if (isSensitiveKey(key)) return REDACTED;
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> nested.put(String.valueOf(nestedKey), redactValue(String.valueOf(nestedKey), nestedValue)));
            return nested;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> list = new ArrayList<>();
            for (Object item : iterable) list.add(redactValue(key, item));
            return list;
        }
        if (value instanceof String s) return redactText(s);
        return value;
    }

    public static String redactText(String value) {
        if (value == null) return null;
        String sanitized = value;
        sanitized = BEARER.matcher(sanitized).replaceAll("$1" + REDACTED);
        sanitized = PRIVATE_TOKEN.matcher(sanitized).replaceAll("$1" + REDACTED);
        sanitized = JSON_SECRET.matcher(sanitized).replaceAll("$1" + REDACTED + "$2");
        sanitized = QUERY_SECRET.matcher(sanitized).replaceAll("$1" + REDACTED);
        sanitized = USERINFO_SECRET.matcher(sanitized).replaceAll("$1" + REDACTED + "@");
        if (sanitized.length() > DEFAULT_MAX_TEXT_LENGTH) {
            return sanitized.substring(0, DEFAULT_MAX_TEXT_LENGTH) + "... [truncated]";
        }
        return sanitized;
    }

    public static String redactThrowableMessage(Throwable throwable) {
        if (throwable == null) return null;
        String message = throwable.getMessage();
        return redactText(message == null || message.isBlank() ? throwable.getClass().getName() : message);
    }

    public static Duration safeHttpTimeout(Duration configured, Duration fallback) {
        Duration base = fallback == null ? Duration.ofSeconds(30) : fallback;
        if (configured == null || configured.isZero() || configured.isNegative()) return base;
        if (configured.compareTo(Duration.ofSeconds(1)) < 0) return Duration.ofSeconds(1);
        if (configured.compareTo(Duration.ofMinutes(5)) > 0) return Duration.ofMinutes(5);
        return configured;
    }
}
