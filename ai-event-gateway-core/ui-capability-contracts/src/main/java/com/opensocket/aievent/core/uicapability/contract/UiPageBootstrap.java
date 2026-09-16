package com.opensocket.aievent.core.uicapability.contract;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** RSC-safe first-paint projection. Resource titles and raw authorization evidence are deliberately absent. */
public record UiPageBootstrap(
        String contractVersion,
        String routeContext,
        String canonicalPath,
        String tenantId,
        long principalEpoch,
        long catalogRevision,
        long policyVersion,
        UiPageBootstrapOutcome outcome,
        List<UiCapability> layoutCapabilities,
        List<UiCapability> pageCapabilities,
        String resourceSummaryRef,
        Long resourceVersion,
        Instant expiresAt,
        String hydrationNonce) {

    public UiPageBootstrap {
        contractVersion = require(contractVersion, "contractVersion");
        if (!UiCapabilityContract.VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unsupported UI capability contract version: " + contractVersion);
        }
        routeContext = require(routeContext, "routeContext");
        canonicalPath = requireCanonicalPath(canonicalPath);
        tenantId = require(tenantId, "tenantId");
        principalEpoch = UiWireNumbers.requireSafeNonNegative(principalEpoch, "principalEpoch");
        catalogRevision = UiWireNumbers.requireSafeNonNegative(catalogRevision, "catalogRevision");
        policyVersion = UiWireNumbers.requireSafeNonNegative(policyVersion, "policyVersion");
        outcome = Objects.requireNonNull(outcome, "outcome");
        layoutCapabilities = layoutCapabilities == null ? List.of() : List.copyOf(layoutCapabilities);
        pageCapabilities = pageCapabilities == null ? List.of() : List.copyOf(pageCapabilities);
        resourceSummaryRef = normalize(resourceSummaryRef);
        resourceVersion = UiWireNumbers.requireSafeNonNegative(resourceVersion, "resourceVersion");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt is required");
        hydrationNonce = require(hydrationNonce, "hydrationNonce");
        if (outcome == UiPageBootstrapOutcome.PAGE && resourceSummaryRef.isBlank()) {
            throw new IllegalArgumentException("PAGE bootstrap requires an opaque resourceSummaryRef");
        }
    }

    private static String requireCanonicalPath(String value) {
        value = require(value, "canonicalPath");
        if (!value.startsWith("/") || value.startsWith("//") || value.contains("://")) {
            throw new IllegalArgumentException("canonicalPath must be a same-origin absolute path");
        }
        return value;
    }
    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
