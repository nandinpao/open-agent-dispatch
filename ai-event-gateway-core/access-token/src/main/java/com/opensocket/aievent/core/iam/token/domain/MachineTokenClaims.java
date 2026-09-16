package com.opensocket.aievent.core.iam.token.domain;

import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Canonical signed claims for short-lived OpenDispatch machine access tokens. */
public record MachineTokenClaims(
        String issuer,
        String subject,
        String principalType,
        String tenantId,
        String credentialId,
        String clientId,
        String jwtId,
        Set<String> scopes,
        Set<String> audiences,
        Set<String> sourceSystems,
        Set<String> apiPrefixes,
        Set<String> cidrs,
        SecurityEpoch securityEpoch,
        Instant issuedAt,
        Instant notBefore,
        Instant expiresAt) {

    public MachineTokenClaims {
        issuer = required(issuer, "issuer", 512);
        subject = required(subject, "subject", 128);
        principalType = required(principalType, "principalType", 64);
        tenantId = required(tenantId, "tenantId", 128);
        credentialId = required(credentialId, "credentialId", 128);
        clientId = required(clientId, "clientId", 96);
        jwtId = required(jwtId, "jwtId", 128);
        scopes = clean(scopes, "scope");
        audiences = clean(audiences, "audience");
        sourceSystems = clean(sourceSystems, "sourceSystem");
        apiPrefixes = clean(apiPrefixes, "apiPrefix");
        cidrs = clean(cidrs, "cidr");
        securityEpoch = securityEpoch == null ? SecurityEpoch.ZERO : securityEpoch;
        if (scopes.isEmpty()) throw new IllegalArgumentException("At least one machine scope is required");
        if (audiences.size() != 1) throw new IllegalArgumentException("Exactly one audience is required");
        if (issuedAt == null || notBefore == null || expiresAt == null) throw new IllegalArgumentException("JWT timestamps are required");
        if (notBefore.isBefore(issuedAt)) throw new IllegalArgumentException("notBefore must not precede issuedAt");
        if (!expiresAt.isAfter(notBefore)) throw new IllegalArgumentException("expiresAt must be after notBefore");
    }

    /** Resource servers must compare this signed snapshot to current IAM authority before accepting the token. */
    public boolean hasCurrentSecurityEpoch(SecurityEpoch current) {
        if (current == null) return false;
        return securityEpoch.isAtLeast(current) && current.isAtLeast(securityEpoch);
    }

    private static Set<String> clean(Collection<String> values, String field) {
        if (values == null || values.isEmpty()) return Set.of();
        TreeSet<String> out = new TreeSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
            out.add(value.trim());
        }
        return Collections.unmodifiableSet(out);
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return normalized;
    }
}
