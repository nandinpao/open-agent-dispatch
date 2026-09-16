package com.opensocket.aievent.core.integration.issue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.integration.issue.webhook.CanonicalExternalObservation;

/** Produces stable canonical JSON before hashing and rejects secret-bearing Provider fields recursively. */
@Component
public class ExternalObservationNormalizer {
    public static final String PROFILE_VERSION = "phase3f-r2-v1";
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "password","secret","credential","authorization","apikey","accesstoken","refreshtoken",
            "dispatchtoken","fencingtoken","privatekey","sessioncookie","rawpayload");
    private final ObjectMapper json;

    public ExternalObservationNormalizer(ObjectMapper json) { this.json = json; }

    public void assertNoSensitiveKeys(String rawJson) {
        String raw = rawJson == null || rawJson.isBlank() ? "{}" : rawJson.trim();
        if (!(raw.startsWith("{") || raw.startsWith("["))) throw new IllegalArgumentException("Webhook payload must be JSON.");
        try { rejectSensitive(json.readValue(raw,Object.class),"$"); }
        catch (IllegalStateException | IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalArgumentException("Webhook payload JSON is invalid.",ex); }
    }

    public CanonicalExternalObservation normalize(String rawJson) {
        String raw = rawJson == null || rawJson.isBlank() ? "{}" : rawJson.trim();
        if (!(raw.startsWith("{") || raw.startsWith("["))) throw new IllegalArgumentException("Webhook observation must be JSON.");
        try {
            Object parsed = json.readValue(raw,Object.class);
            rejectSensitive(parsed,"$");
            Object canonicalValue = canonicalize(parsed);
            String canonical = json.writeValueAsString(canonicalValue);
            return new CanonicalExternalObservation(raw,canonical,sha256(canonical),PROFILE_VERSION);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Webhook observation JSON is invalid.",ex);
        }
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?,?> map) {
            Map<String,Object> sorted = new TreeMap<>();
            map.forEach((key,item) -> sorted.put(String.valueOf(key),canonicalize(item)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> ordered = new ArrayList<>(list.size());
            for (Object item : list) ordered.add(canonicalize(item));
            return ordered;
        }
        if (value instanceof Number number) {
            try { return new BigDecimal(number.toString()).stripTrailingZeros(); }
            catch (NumberFormatException ignored) { return number; }
        }
        return value;
    }

    private void rejectSensitive(Object value,String path) {
        if (value instanceof Map<?,?> map) {
            for (Map.Entry<?,?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
                if (FORBIDDEN_KEYS.contains(normalized)) throw new IllegalStateException("WEBHOOK_SENSITIVE_FIELD_BLOCKED:"+path+"/"+key);
                rejectSensitive(entry.getValue(),path+"/"+key);
            }
        } else if (value instanceof List<?> list) {
            for (int i=0;i<list.size();i++) rejectSensitive(list.get(i),path+"/"+i);
        }
    }

    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((value==null?"":value).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable.",ex); }
    }
}
