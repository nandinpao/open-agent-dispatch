package com.opensocket.aievent.core.api.contract;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core.api-contract")
public class ApiContractProperties {
    private ApiContractEnforcementMode enforcement = ApiContractEnforcementMode.AUDIT;
    private boolean requireIdempotency = true;
    private boolean requireCorrelationId = true;
    private boolean requireExpectedVersion = true;
    private boolean requireActorIdentity = true;
    private boolean requireAuditReason = true;
    private Set<String> expectedVersionPathFragments = new LinkedHashSet<>(Set.of(
            "/transitions", "/approve", "/reject", "/resolve", "/retry",
            "/rotate-credential", "/security-overrides", "/revoke", "/cancel"));
    private Set<String> auditReasonExemptPathPrefixes = new LinkedHashSet<>(Set.of(
            "/api/session/", "/api/events/", "/api/agent/", "/api/integrations/webhooks/"));

    public ApiContractEnforcementMode getEnforcement() { return enforcement; }
    public void setEnforcement(ApiContractEnforcementMode value) {
        enforcement = value == null ? ApiContractEnforcementMode.AUDIT : value;
    }
    public boolean isRequireIdempotency() { return requireIdempotency; }
    public void setRequireIdempotency(boolean value) { requireIdempotency = value; }
    public boolean isRequireCorrelationId() { return requireCorrelationId; }
    public void setRequireCorrelationId(boolean value) { requireCorrelationId = value; }
    public boolean isRequireExpectedVersion() { return requireExpectedVersion; }
    public void setRequireExpectedVersion(boolean value) { requireExpectedVersion = value; }
    public boolean isRequireActorIdentity() { return requireActorIdentity; }
    public void setRequireActorIdentity(boolean value) { requireActorIdentity = value; }
    public boolean isRequireAuditReason() { return requireAuditReason; }
    public void setRequireAuditReason(boolean value) { requireAuditReason = value; }
    public Set<String> getExpectedVersionPathFragments() { return expectedVersionPathFragments; }
    public void setExpectedVersionPathFragments(Set<String> value) {
        expectedVersionPathFragments = value == null ? new LinkedHashSet<>() : new LinkedHashSet<>(value);
    }
    public Set<String> getAuditReasonExemptPathPrefixes() { return auditReasonExemptPathPrefixes; }
    public void setAuditReasonExemptPathPrefixes(Set<String> value) {
        auditReasonExemptPathPrefixes = value == null ? new LinkedHashSet<>() : new LinkedHashSet<>(value);
    }
}
