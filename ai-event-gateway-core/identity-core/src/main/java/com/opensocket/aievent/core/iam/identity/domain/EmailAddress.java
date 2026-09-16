package com.opensocket.aievent.core.iam.identity.domain;

import java.text.Normalizer;
import java.util.Locale;

public record EmailAddress(String value, String normalizedValue) {
    public EmailAddress(String value) {
        this(value, normalize(value));
    }

    public EmailAddress {
        value = DomainText.required(value, "email", 320);
        normalizedValue = DomainText.required(normalizedValue, "normalizedEmail", 320);
        String expected = normalize(value);
        if (!expected.equals(normalizedValue)) {
            throw new IllegalArgumentException("normalizedEmail does not match email");
        }
        int at = normalizedValue.indexOf('@');
        if (at <= 0 || at == normalizedValue.length() - 1 || normalizedValue.indexOf('@', at + 1) >= 0) {
            throw new IllegalArgumentException("email must contain one local and domain part");
        }
    }

    private static String normalize(String value) {
        String checked = DomainText.required(value, "email", 320);
        return Normalizer.normalize(checked, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
