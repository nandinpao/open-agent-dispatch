package com.opensocket.aievent.core.iam.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Phase 9 cutover policy for the external Event Intake resource server. */
@ConfigurationProperties("aeg.iam.event-intake")
public class EventIntakeSecurityProperties {
    public enum Mode { LEGACY_ONLY, SHADOW, DUAL_ACCEPT, JWT_REQUIRED }

    private Mode mode = Mode.LEGACY_ONLY;
    private String requiredScope = "events.intake";
    private String audience = "opendispatch-event-api";
    private boolean requireApiPrefix = true;
    private boolean auditEnabled = true;

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode == null ? Mode.LEGACY_ONLY : mode; }
    public String getRequiredScope() { return requiredScope; }
    public void setRequiredScope(String value) { requiredScope = normalize(value, "events.intake"); }
    public String getAudience() { return audience; }
    public void setAudience(String value) { audience = normalize(value, "opendispatch-event-api"); }
    public boolean isRequireApiPrefix() { return requireApiPrefix; }
    public void setRequireApiPrefix(boolean value) { requireApiPrefix = value; }
    public boolean isAuditEnabled() { return auditEnabled; }
    public void setAuditEnabled(boolean value) { auditEnabled = value; }

    public boolean acceptsLegacy() { return mode != Mode.JWT_REQUIRED; }
    public boolean acceptsJwt() { return mode != Mode.LEGACY_ONLY; }
    public boolean shadowOnly() { return mode == Mode.SHADOW; }
    public boolean jwtRequired() { return mode == Mode.JWT_REQUIRED; }

    public void validate() {
        if (requiredScope == null || requiredScope.isBlank()) throw new IllegalStateException("Event Intake required scope is required");
        if (audience == null || audience.isBlank()) throw new IllegalStateException("Event Intake audience is required");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
