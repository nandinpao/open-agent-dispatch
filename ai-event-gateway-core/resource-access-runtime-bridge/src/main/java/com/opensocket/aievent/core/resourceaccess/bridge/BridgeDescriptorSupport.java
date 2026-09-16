package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.DescriptorFingerprint;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;

final class BridgeDescriptorSupport {
    private BridgeDescriptorSupport() {}
    static String owner(String value) { return value == null || value.isBlank() || "UNASSIGNED".equalsIgnoreCase(value) ? "" : value.trim(); }
    static SensitivityLevel sensitivity(String value, SensitivityLevel fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return SensitivityLevel.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }
    static VisibilityDescriptor visibility(SensitivityLevel sensitivity, VisibilityLevel maximum, String policy, long revision) {
        String canonical = policy == null ? "" : policy.trim();
        return new VisibilityDescriptor(sensitivity, maximum, canonical,
                new PolicyVersion(1, Math.max(0, revision), DescriptorFingerprint.sha256(canonical+":"+revision)));
    }
    static Instant instant(OffsetDateTime value, Instant fallback) { return value == null ? fallback : value.toInstant(); }
    static long revisionToken(String canonical) {
        String hash=DescriptorFingerprint.sha256(canonical);
        return Long.parseUnsignedLong(hash.substring(0, 15), 16);
    }
    static String participantId(ResourceRef ref, ResourceParticipantType type, String participantRef, ResourceParticipantRole role) {
        return "rp-"+DescriptorFingerprint.sha256(ref.tenantId()+":"+ref.resourceType()+":"+ref.resourceId()+":"+type+":"+participantRef+":"+role).substring(0,32);
    }
    static String hash(ResourceRef ref, String canonical) {
        return DescriptorFingerprint.sha256(ref.tenantId()+"|"+ref.resourceType()+"|"+ref.resourceId()+"|"+canonical);
    }
    static ResourceSecurityState stateForDisabled(boolean disabled) { return disabled ? ResourceSecurityState.RESTRICTED : ResourceSecurityState.NORMAL; }
}
