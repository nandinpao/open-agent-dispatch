package com.opensocket.aievent.core.resourceaccess.contract;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant;
import java.util.Objects;

/** Expiring and fenceable authorization evidence for operations longer than one request. */
public record RuntimeAuthorizationLease(
        String leaseId,
        String authorizationDecisionId,
        String descriptorHash,
        ResourceRef resourceRef,
        PrincipalRef principal,
        String permissionCode,
        String assignmentId,
        Integer attemptNo,
        PolicyVersion issuedPolicyVersion,
        SecurityEpoch issuedEpoch,
        long fencingVersion,
        Instant issuedAt,
        Instant expiresAt,
        Instant maximumStaleUntil,
        Instant recheckAfter,
        RuntimeLeaseStatus status,
        long version) {
    public RuntimeAuthorizationLease {
        leaseId = requireText(leaseId, "leaseId");
        authorizationDecisionId = requireText(authorizationDecisionId, "authorizationDecisionId");
        descriptorHash = requireText(descriptorHash, "descriptorHash");
        Objects.requireNonNull(resourceRef, "resourceRef");
        Objects.requireNonNull(principal, "principal");
        permissionCode = requireText(permissionCode, "permissionCode");
        assignmentId = assignmentId == null ? "" : assignmentId.trim();
        if (attemptNo != null && attemptNo < 0) throw new IllegalArgumentException("attemptNo must be non-negative");
        issuedPolicyVersion = issuedPolicyVersion == null ? PolicyVersion.ZERO : issuedPolicyVersion;
        issuedEpoch = issuedEpoch == null ? SecurityEpoch.ZERO : issuedEpoch;
        if (fencingVersion < 0 || version < 1) throw new IllegalArgumentException("lease versions are invalid");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(maximumStaleUntil, "maximumStaleUntil");
        Objects.requireNonNull(recheckAfter, "recheckAfter");
        Objects.requireNonNull(status, "status");
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("expiresAt must be after issuedAt");
        if (maximumStaleUntil.isAfter(expiresAt)) throw new IllegalArgumentException("maximumStaleUntil must not exceed expiresAt");
        if (recheckAfter.isAfter(maximumStaleUntil)) throw new IllegalArgumentException("recheckAfter must not exceed maximumStaleUntil");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
