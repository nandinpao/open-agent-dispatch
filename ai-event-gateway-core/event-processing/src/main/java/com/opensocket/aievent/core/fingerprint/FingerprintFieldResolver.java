package com.opensocket.aievent.core.fingerprint;

import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.event.NormalizedEvent;

@Component
public class FingerprintFieldResolver {
    private final DynamicTokenMasker masker;

    @Autowired
    public FingerprintFieldResolver(DynamicTokenMasker masker) {
        this.masker = masker;
    }

    public FingerprintFieldResolver() {
        this(new DynamicTokenMasker());
    }

    public String resolve(String field, NormalizedEvent event) {
        if (field == null || field.isBlank()) {
            return "";
        }
        String key = field.trim();
        return switch (key) {
            case "tenantId" -> safe(event.tenantId());
            case "sourceSystem" -> safe(event.sourceSystem());
            case "siteId" -> safe(event.siteId());
            case "plantId" -> safe(event.plantId());
            case "objectType" -> safe(event.objectType());
            case "objectId" -> safe(event.objectId());
            case "eventType" -> safe(event.eventType());
            case "errorCode" -> safe(event.errorCode());
            case "severity" -> event.severity() == null ? "" : event.severity().name();
            case "normalizedMessage" -> safe(event.normalizedMessage());
            case "maskedMessage" -> masker.mask(event.normalizedMessage());
            default -> resolveDynamicField(key, event.attributes());
        };
    }

    private String resolveDynamicField(String key, Map<String, Object> attributes) {
        if (key.startsWith("attribute:")) {
            return normalizeAttributeValue(attributes.get(key.substring("attribute:".length())));
        }
        if (key.startsWith("attr:")) {
            return normalizeAttributeValue(attributes.get(key.substring("attr:".length())));
        }
        return "";
    }

    private String normalizeAttributeValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim().replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
