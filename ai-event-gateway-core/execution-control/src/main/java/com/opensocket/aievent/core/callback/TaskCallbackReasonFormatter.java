package com.opensocket.aievent.core.callback;

/** Pure callback reason formatting support; it owns no callback state transition authority. */
final class TaskCallbackReasonFormatter {
    private TaskCallbackReasonFormatter() {
    }

    static String suffix(String message) {
        return message == null || message.isBlank() ? "" : ": " + message;
    }

    static String progressSuffix(TaskCallbackRequest callback) {
        String progress = callback.getProgressPercent() == null ? "" : " " + callback.getProgressPercent() + "%";
        return progress + suffix(callback.getMessage());
    }
}
