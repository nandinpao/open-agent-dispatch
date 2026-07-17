package com.opensocket.aievent.gateway.netty.inbound;

/** Transport-level result for inbound event capture and optional Core forwarding. */
public enum InboundForwardStatus {
    FORWARDED,
    FORWARD_QUEUED,
    FORWARD_QUEUE_FULL,
    FORWARD_DISABLED,
    FORWARD_SKIPPED_BY_CATEGORY,
    FORWARD_FAILED,
    FORWARD_TIMEOUT
}
