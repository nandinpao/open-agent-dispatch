package com.opensocket.aievent.core.dispatch;

/** Final pre-send authority decision with typed failure semantics. */
public record DispatchPreSendAdmissionDecision(
        boolean applicable,
        boolean allowed,
        DispatchFailureDisposition disposition,
        String reasonCode,
        String message,
        DispatchSendPermit permit) {

    public DispatchPreSendAdmissionDecision {
        if (allowed && disposition != DispatchFailureDisposition.NONE) {
            throw new IllegalArgumentException("Allowed pre-send decision must use NONE disposition");
        }
        if (!allowed && (disposition == null || disposition == DispatchFailureDisposition.NONE)) {
            throw new IllegalArgumentException("Blocked pre-send decision requires a failure disposition");
        }
    }

    public static DispatchPreSendAdmissionDecision notApplicable(String message) {
        return new DispatchPreSendAdmissionDecision(false, true, DispatchFailureDisposition.NONE, "NOT_APPLICABLE", message, null);
    }

    public static DispatchPreSendAdmissionDecision allow(String reasonCode, String message, DispatchSendPermit permit) {
        if (permit == null) throw new IllegalArgumentException("permit is required for an allowed applicable admission");
        return new DispatchPreSendAdmissionDecision(true, true, DispatchFailureDisposition.NONE, reasonCode, message, permit);
    }

    /** Compatibility default for deterministic policy denial; new callers should use a typed factory. */
    public static DispatchPreSendAdmissionDecision block(String reasonCode, String message) {
        return blockPolicy(reasonCode, message);
    }

    public static DispatchPreSendAdmissionDecision retryInfrastructure(String reasonCode, String message) {
        return new DispatchPreSendAdmissionDecision(true, false, DispatchFailureDisposition.RETRY_INFRASTRUCTURE, reasonCode, message, null);
    }

    public static DispatchPreSendAdmissionDecision reassignRequired(String reasonCode, String message) {
        return new DispatchPreSendAdmissionDecision(true, false, DispatchFailureDisposition.REASSIGN_REQUIRED, reasonCode, message, null);
    }

    public static DispatchPreSendAdmissionDecision blockPolicy(String reasonCode, String message) {
        return new DispatchPreSendAdmissionDecision(true, false, DispatchFailureDisposition.BLOCK_POLICY, reasonCode, message, null);
    }

    public static DispatchPreSendAdmissionDecision blockSecurity(String reasonCode, String message) {
        return new DispatchPreSendAdmissionDecision(true, false, DispatchFailureDisposition.BLOCK_SECURITY, reasonCode, message, null);
    }

    public static DispatchPreSendAdmissionDecision terminal(String reasonCode, String message) {
        return new DispatchPreSendAdmissionDecision(true, false, DispatchFailureDisposition.TERMINAL_FAILURE, reasonCode, message, null);
    }
}
