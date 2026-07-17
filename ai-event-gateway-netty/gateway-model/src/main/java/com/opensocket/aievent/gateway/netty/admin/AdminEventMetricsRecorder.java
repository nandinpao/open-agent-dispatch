package com.opensocket.aievent.gateway.netty.admin;

/** Port for recording gateway routing metrics without coupling the core to Admin UI adapters. */
public interface AdminEventMetricsRecorder {

    default void recordInbound() {
        // Optional for minimal adapters.
    }

    void recordRouted();

    void recordFailed();

    static AdminEventMetricsRecorder noop() {
        return new AdminEventMetricsRecorder() {
            @Override
            public void recordRouted() {
                // no-op
            }

            @Override
            public void recordFailed() {
                // no-op
            }
        };
    }
}
