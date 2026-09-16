package com.opensocket.aievent.core.iam.security.contract;

/**
 * Canonical machine identity kinds. The type is intentionally independent of Spring Security,
 * persistence records, token formats, and protocol-specific authentication mechanisms.
 */
public enum MachinePrincipalType {
    SERVICE_ACCOUNT,
    INTEGRATION,
    AGENT,
    A2A_AGENT,
    SYSTEM_SERVICE
}
