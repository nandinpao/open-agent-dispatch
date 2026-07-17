package com.opensocket.aievent.core.fingerprint;

import java.util.List;

public record FingerprintTemplate(String policyName, List<String> fields) {
    public FingerprintTemplate {
        policyName = policyName == null || policyName.isBlank() ? "default" : policyName.trim();
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
