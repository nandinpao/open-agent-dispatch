package com.opensocket.aievent.core.dispatch;

/**
 * Typed C0-A3 failure semantics for pre-network dispatch authority decisions.
 *
 * <p>The disposition controls recovery behavior; reasonCode is evidence only and must not be
 * parsed to infer whether a failure is retryable, requires reassignment, or is a policy/security
 * block.</p>
 */
public enum DispatchFailureDisposition {
    NONE,
    RETRY_INFRASTRUCTURE,
    REASSIGN_REQUIRED,
    BLOCK_POLICY,
    BLOCK_SECURITY,
    TERMINAL_FAILURE
}
