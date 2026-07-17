package com.opensocket.aievent.core.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/** Shared constant-time verifier used by authentication and CSRF compatibility checks. */
public class CoreInternalTokenVerifier {
    private final CoreInternalSecurityProperties properties;
    private final CoreInternalSecurityRequestClassifier classifier;

    public CoreInternalTokenVerifier(CoreInternalSecurityProperties properties,
                                     CoreInternalSecurityRequestClassifier classifier) {
        this.properties = properties;
        this.classifier = classifier;
    }

    public Verification verify(HttpServletRequest request) {
        Optional<CoreInternalSecurityRole> required = classifier.requiredRole(request);
        if (!properties.isEnabled() || required.isEmpty()) {
            return Verification.notRequired();
        }
        CoreInternalSecurityRole role = required.get();
        String configured = properties.tokenFor(role);
        if (configured.isBlank()) {
            return Verification.rejected(role, "missing_configured_token");
        }
        String supplied = suppliedToken(request);
        if (supplied.isBlank()) {
            return Verification.rejected(role, "missing_request_token");
        }
        return constantTimeEquals(configured, supplied)
                ? Verification.accepted(role)
                : Verification.rejected(role, "invalid_request_token");
    }

    public boolean isValidInternalTokenRequest(HttpServletRequest request) {
        Verification verification = verify(request);
        return verification.required() && verification.accepted();
    }

    private String suppliedToken(HttpServletRequest request) {
        String primary = request.getHeader(properties.getTokenHeaderName());
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (properties.isAllowLegacyTokenHeader()) {
            String legacy = request.getHeader(properties.getLegacyTokenHeaderName());
            if (legacy != null && !legacy.isBlank()) {
                return legacy.trim();
            }
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return "";
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    public record Verification(boolean required, boolean accepted, CoreInternalSecurityRole role, String reason) {
        static Verification notRequired() { return new Verification(false, false, null, "not_required"); }
        static Verification accepted(CoreInternalSecurityRole role) { return new Verification(true, true, role, "accepted"); }
        static Verification rejected(CoreInternalSecurityRole role, String reason) { return new Verification(true, false, role, reason); }
    }
}
