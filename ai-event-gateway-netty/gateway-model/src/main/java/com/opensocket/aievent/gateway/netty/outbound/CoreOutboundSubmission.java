package com.opensocket.aievent.gateway.netty.outbound;

public record CoreOutboundSubmission(
        CoreOutboundStatus status,
        String message,
        int queueSize,
        int queueRemainingCapacity
) {
    public boolean accepted() {
        return status == CoreOutboundStatus.SUBMITTED;
    }

    public static CoreOutboundSubmission submitted(int queueSize, int queueRemainingCapacity) {
        return new CoreOutboundSubmission(CoreOutboundStatus.SUBMITTED, "Core outbound request submitted", queueSize, queueRemainingCapacity);
    }

    public static CoreOutboundSubmission disabled(int queueSize, int queueRemainingCapacity) {
        return new CoreOutboundSubmission(CoreOutboundStatus.DISABLED, "Core outbound dispatcher is disabled", queueSize, queueRemainingCapacity);
    }

    public static CoreOutboundSubmission queueFull(int queueSize, int queueRemainingCapacity) {
        return new CoreOutboundSubmission(CoreOutboundStatus.QUEUE_FULL, "Core outbound queue is full", queueSize, queueRemainingCapacity);
    }

    public static CoreOutboundSubmission failed(String message, int queueSize, int queueRemainingCapacity) {
        return new CoreOutboundSubmission(CoreOutboundStatus.FAILED, message == null ? "Core outbound request submission failed" : message, queueSize, queueRemainingCapacity);
    }
}
