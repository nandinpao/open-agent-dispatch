package com.opensocket.aievent.core.security;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core.security.internal")
public class CoreInternalSecurityProperties {
    private boolean enabled = false;
    private String tokenHeaderName = "X-Cluster-Token";
    private String legacyTokenHeaderName = "X-Internal-Token";
    private boolean allowLegacyTokenHeader = true;
    private boolean protectApiMutations = false;
    private boolean permitActuatorHealthInfo = true;
    private boolean auditLogEnabled = true;
    private String gatewayToken = "";
    private String adapterWorkerToken = "";
    private String eventIntakeToken = "";
    private String operatorToken = "";
    private String recoveryOperatorToken = "";
    private String recoveryAdminToken = "";
    private String recoveryApproverToken = "";
    private String actuatorToken = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTokenHeaderName() { return tokenHeaderName; }
    public void setTokenHeaderName(String tokenHeaderName) { this.tokenHeaderName = normalizeHeader(tokenHeaderName, "X-Cluster-Token"); }
    public String getLegacyTokenHeaderName() { return legacyTokenHeaderName; }
    public void setLegacyTokenHeaderName(String legacyTokenHeaderName) { this.legacyTokenHeaderName = normalizeHeader(legacyTokenHeaderName, "X-Internal-Token"); }
    public boolean isAllowLegacyTokenHeader() { return allowLegacyTokenHeader; }
    public void setAllowLegacyTokenHeader(boolean allowLegacyTokenHeader) { this.allowLegacyTokenHeader = allowLegacyTokenHeader; }
    public boolean isProtectApiMutations() { return protectApiMutations; }
    public void setProtectApiMutations(boolean protectApiMutations) { this.protectApiMutations = protectApiMutations; }
    public boolean isPermitActuatorHealthInfo() { return permitActuatorHealthInfo; }
    public void setPermitActuatorHealthInfo(boolean permitActuatorHealthInfo) { this.permitActuatorHealthInfo = permitActuatorHealthInfo; }
    public boolean isAuditLogEnabled() { return auditLogEnabled; }
    public void setAuditLogEnabled(boolean auditLogEnabled) { this.auditLogEnabled = auditLogEnabled; }
    public String getGatewayToken() { return gatewayToken; }
    public void setGatewayToken(String gatewayToken) { this.gatewayToken = normalizeToken(gatewayToken); }
    public String getAdapterWorkerToken() { return adapterWorkerToken; }
    public void setAdapterWorkerToken(String adapterWorkerToken) { this.adapterWorkerToken = normalizeToken(adapterWorkerToken); }
    public String getEventIntakeToken() { return eventIntakeToken; }
    public void setEventIntakeToken(String eventIntakeToken) { this.eventIntakeToken = normalizeToken(eventIntakeToken); }
    public String getOperatorToken() { return operatorToken; }
    public void setOperatorToken(String operatorToken) { this.operatorToken = normalizeToken(operatorToken); }
    public String getRecoveryOperatorToken() { return recoveryOperatorToken; }
    public void setRecoveryOperatorToken(String recoveryOperatorToken) { this.recoveryOperatorToken = normalizeToken(recoveryOperatorToken); }
    public String getRecoveryAdminToken() { return recoveryAdminToken; }
    public void setRecoveryAdminToken(String recoveryAdminToken) { this.recoveryAdminToken = normalizeToken(recoveryAdminToken); }
    public String getRecoveryApproverToken() { return recoveryApproverToken; }
    public void setRecoveryApproverToken(String recoveryApproverToken) { this.recoveryApproverToken = normalizeToken(recoveryApproverToken); }
    public String getActuatorToken() { return actuatorToken; }
    public void setActuatorToken(String actuatorToken) { this.actuatorToken = normalizeToken(actuatorToken); }

    public boolean hasTokenFor(CoreInternalSecurityRole role) {
        return !tokenFor(role).isBlank();
    }

    public String tokenFor(CoreInternalSecurityRole role) {
        return switch (role) {
            case GATEWAY -> gatewayToken;
            case ADAPTER_WORKER -> adapterWorkerToken;
            case EVENT_INGESTION -> !eventIntakeToken.isBlank() ? eventIntakeToken : operatorToken;
            case OPERATOR -> operatorToken;
            case RECOVERY_OPERATOR -> !recoveryOperatorToken.isBlank() ? recoveryOperatorToken : operatorToken;
            case RECOVERY_ADMIN -> !recoveryAdminToken.isBlank() ? recoveryAdminToken : operatorToken;
            case RECOVERY_APPROVER -> !recoveryApproverToken.isBlank() ? recoveryApproverToken : (!recoveryAdminToken.isBlank() ? recoveryAdminToken : operatorToken);
            case ACTUATOR -> actuatorToken;
        };
    }

    public Map<CoreInternalSecurityRole, Boolean> configuredRoles() {
        EnumMap<CoreInternalSecurityRole, Boolean> configured = new EnumMap<>(CoreInternalSecurityRole.class);
        for (CoreInternalSecurityRole role : CoreInternalSecurityRole.values()) {
            configured.put(role, hasTokenFor(role));
        }
        return configured;
    }

    private static String normalizeHeader(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim();
    }

    public static String authority(CoreInternalSecurityRole role) {
        return "ROLE_" + role.name().toUpperCase(Locale.ROOT);
    }
}
