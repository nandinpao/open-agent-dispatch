package com.opensocket.aievent.core.security.audit;

/**
 * Reliability boundary used when a primary security audit sink fails.
 * Implementations must never replace the original authentication/authorization outcome.
 */
@FunctionalInterface
public interface SecurityAuditFailureReporter {
    void report(String component,String auditType,String tenantId,String principalId,
                String correlationId,String reasonCode,RuntimeException failure);

    static SecurityAuditFailureReporter noop(){return (component,auditType,tenantId,principalId,correlationId,reasonCode,failure)->{};}
}
