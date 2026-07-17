package com.opensocket.aievent.gateway.netty.model;

/**
 * Marker for the canonical Netty gateway data-model module.
 *
 * <p>This module owns protocol envelopes, transport DTOs, authorization DTOs,
 * runtime snapshots, cluster snapshots, and outbound result value types. It must not
 * own Netty channel handlers, Spring controllers, schedulers, clients, registries, or services.</p>
 */
public final class NettyDataModelModule {
    private NettyDataModelModule() {
    }
}
