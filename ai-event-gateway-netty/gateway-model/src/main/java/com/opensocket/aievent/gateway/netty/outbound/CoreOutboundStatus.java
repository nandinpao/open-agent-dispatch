package com.opensocket.aievent.gateway.netty.outbound;

/** Result status for bounded Core outbound submissions and worker execution. */
public enum CoreOutboundStatus {
    SUBMITTED,
    DISABLED,
    QUEUE_FULL,
    SUCCEEDED,
    HTTP_ERROR,
    TIMEOUT,
    FAILED,
    INTERRUPTED
}
