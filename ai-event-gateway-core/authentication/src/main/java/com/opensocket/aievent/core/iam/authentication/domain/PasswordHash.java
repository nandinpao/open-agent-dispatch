package com.opensocket.aievent.core.iam.authentication.domain;

public record PasswordHash(String algorithm, String encoded) {
    public PasswordHash {
        algorithm = Text.required(algorithm, "algorithm", 64);
        encoded = Text.required(encoded, "encoded", 1024);
    }
}
