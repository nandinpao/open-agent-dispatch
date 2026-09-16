package com.opensocket.aievent.core.iam.identity.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public record Username(String value, String normalizedValue) {
    private static final Set<String> RESERVED = Set.of("root", "system", "agent", "anonymous");

    public Username(String value) {
        this(value, normalize(value));
    }

    public Username {
        value = DomainText.required(value, "username", 128);
        String expected = normalize(value);
        normalizedValue = DomainText.required(normalizedValue, "normalizedUsername", 128);
        if (!expected.equals(normalizedValue)) {
            throw new IllegalArgumentException("normalizedUsername does not match username");
        }
        if (RESERVED.contains(normalizedValue)) {
            throw new IllegalArgumentException("username is reserved");
        }
    }

    private static String normalize(String value) {
        String checked = DomainText.required(value, "username", 128);
        return Normalizer.normalize(checked, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
