package com.opensocket.aievent.core.dispatch;

/** Typed execution-safety result. The disposition, not reason text, owns recovery semantics. */
public record DispatchExecutionSafetyDecision(
        boolean allowed,
        DispatchFailureDisposition disposition,
        String reasonCode,
        String message) {

    public DispatchExecutionSafetyDecision {
        if (allowed && disposition != DispatchFailureDisposition.NONE) {
            throw new IllegalArgumentException("Allowed safety decision must use NONE disposition");
        }
        if (!allowed && (disposition == null || disposition == DispatchFailureDisposition.NONE)) {
            throw new IllegalArgumentException("Blocked safety decision requires a failure disposition");
        }
    }

    public static DispatchExecutionSafetyDecision allow(String message) {
        return new DispatchExecutionSafetyDecision(true, DispatchFailureDisposition.NONE, "ALLOWED", message);
    }

    /** Compatibility default for deterministic policy denial; new callers should use a typed factory. */
    public static DispatchExecutionSafetyDecision block(String reasonCode, String message) {
        return blockPolicy(reasonCode, message);
    }

    public static DispatchExecutionSafetyDecision retryInfrastructure(String reasonCode, String message) {
        return new DispatchExecutionSafetyDecision(false, DispatchFailureDisposition.RETRY_INFRASTRUCTURE, reasonCode, message);
    }

    public static DispatchExecutionSafetyDecision reassignRequired(String reasonCode, String message) {
        return new DispatchExecutionSafetyDecision(false, DispatchFailureDisposition.REASSIGN_REQUIRED, reasonCode, message);
    }

    public static DispatchExecutionSafetyDecision blockPolicy(String reasonCode, String message) {
        return new DispatchExecutionSafetyDecision(false, DispatchFailureDisposition.BLOCK_POLICY, reasonCode, message);
    }

    public static DispatchExecutionSafetyDecision blockSecurity(String reasonCode, String message) {
        return new DispatchExecutionSafetyDecision(false, DispatchFailureDisposition.BLOCK_SECURITY, reasonCode, message);
    }

    public static DispatchExecutionSafetyDecision terminal(String reasonCode, String message) {
        return new DispatchExecutionSafetyDecision(false, DispatchFailureDisposition.TERMINAL_FAILURE, reasonCode, message);
    }
}
