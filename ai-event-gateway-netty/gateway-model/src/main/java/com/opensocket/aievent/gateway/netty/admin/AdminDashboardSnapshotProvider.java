package com.opensocket.aievent.gateway.netty.admin;

/**
 * Port used by transport adapters to obtain an Admin dashboard snapshot without depending on the
 * Admin REST/API presentation module.
 */
public interface AdminDashboardSnapshotProvider {

    Object dashboardSnapshot();

    static AdminDashboardSnapshotProvider empty() {
        return () -> null;
    }
}
