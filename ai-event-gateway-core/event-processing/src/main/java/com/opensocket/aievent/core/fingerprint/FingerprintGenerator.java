package com.opensocket.aievent.core.fingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.StringJoiner;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.event.NormalizedEvent;

@Service
public class FingerprintGenerator {
    private final FingerprintPolicyProperties properties;
    private final FingerprintPolicyResolver policyResolver;
    private final FingerprintFieldResolver fieldResolver;

    @Autowired
    public FingerprintGenerator(FingerprintPolicyProperties properties,
                                FingerprintPolicyResolver policyResolver,
                                FingerprintFieldResolver fieldResolver) {
        this.properties = properties;
        this.policyResolver = policyResolver;
        this.fieldResolver = fieldResolver;
    }

    /** Keeps unit tests and manual bootstrap code compatible with the earlier no-arg API. */
    public FingerprintGenerator() {
        this(new FingerprintPolicyProperties(), new FingerprintPolicyResolver(), new FingerprintFieldResolver());
    }

    public String generate(NormalizedEvent event) {
        String base = buildFingerprintBase(event);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(base.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    String buildFingerprintBase(NormalizedEvent event) {
        FingerprintTemplate template = policyResolver.resolve(event);
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(properties.getPolicyVersion() == null ? "v2" : properties.getPolicyVersion());
        joiner.add(template.policyName());
        for (String field : template.fields()) {
            joiner.add(field + "=" + fieldResolver.resolve(field, event));
        }
        return joiner.toString();
    }
}
