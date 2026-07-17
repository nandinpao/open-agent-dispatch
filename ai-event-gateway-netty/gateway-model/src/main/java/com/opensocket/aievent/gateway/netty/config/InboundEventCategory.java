package com.opensocket.aievent.gateway.netty.config;

/**
 * Coarse-grained inbound transport event category used for local history and optional Core
 * forwarding decisions.
 */
public enum InboundEventCategory {
    BUSINESS_EVENT,
    TASK_LIFECYCLE_EVENT,
    TRANSPORT_SIGNAL,
    HEARTBEAT_SIGNAL,
    SYSTEM_SIGNAL
}
