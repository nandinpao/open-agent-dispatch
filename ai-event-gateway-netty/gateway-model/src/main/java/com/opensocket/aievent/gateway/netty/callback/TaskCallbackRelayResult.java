package com.opensocket.aievent.gateway.netty.callback;

public record TaskCallbackRelayResult(
        String taskId,
        String callbackType,
        boolean submitted,
        String status,
        String message
) {
    public static TaskCallbackRelayResult disabled(String callbackType) {
        return new TaskCallbackRelayResult(null, callbackType, false, "RELAY_DISABLED", "Task callback relay is disabled");
    }

    public static TaskCallbackRelayResult skipped(String callbackType, String message) {
        return new TaskCallbackRelayResult(null, callbackType, false, "RELAY_SKIPPED", message);
    }

    public static TaskCallbackRelayResult submitted(String taskId, String callbackType) {
        return new TaskCallbackRelayResult(taskId, callbackType, true, "RELAY_SUBMITTED", "Task callback submitted to bounded Core outbound worker");
    }

    public static TaskCallbackRelayResult coreAccepted(String taskId, String callbackType, int httpStatus) {
        return new TaskCallbackRelayResult(taskId, callbackType, false, "CALLBACK_CORE_ACCEPTED",
                "Task callback accepted by Core callback inbox; httpStatus=" + httpStatus);
    }

    public static TaskCallbackRelayResult coreRejected(String taskId, String callbackType, int httpStatus, String message) {
        return new TaskCallbackRelayResult(taskId, callbackType, false, "CALLBACK_CORE_REJECTED",
                message == null || message.isBlank() ? "Core callback endpoint rejected callback; httpStatus=" + httpStatus : message);
    }

    public static TaskCallbackRelayResult rejected(String taskId, String callbackType, String status, String message) {
        return new TaskCallbackRelayResult(taskId, callbackType, false, status == null ? "RELAY_REJECTED" : status, message);
    }

    public static TaskCallbackRelayResult failed(String taskId, String callbackType, String message) {
        return new TaskCallbackRelayResult(taskId, callbackType, false, "RELAY_FAILED", message);
    }
}
