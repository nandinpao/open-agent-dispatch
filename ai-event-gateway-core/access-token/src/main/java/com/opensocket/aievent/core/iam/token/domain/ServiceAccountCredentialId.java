package com.opensocket.aievent.core.iam.token.domain;

/** Stable identifier for a Service Account client credential. */
public record ServiceAccountCredentialId(String value) {
    public ServiceAccountCredentialId {
        value = TokenText.required(value, "credentialId", 128);
    }
}
