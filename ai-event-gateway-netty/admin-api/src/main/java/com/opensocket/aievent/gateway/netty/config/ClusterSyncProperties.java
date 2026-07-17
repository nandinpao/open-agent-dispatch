package com.opensocket.aievent.gateway.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties holder for Cluster Sync Properties. Values are bound from
 * application.yml and environment variables so local, Docker, cluster, and production deployments
 * can use the same code path.
 */
@ConfigurationProperties(prefix = "cluster.sync")
public record ClusterSyncProperties(
        boolean enabled,
        long intervalMs,
        long requestTimeoutMs,
        long remoteStateTtlMs,
        int maxAgentsPerNode,
        int maxEventsPerNode
) {
    public long safeIntervalMs() {
        return intervalMs <= 0 ? 5000 : intervalMs;
    }

    public long safeRequestTimeoutMs() {
        return requestTimeoutMs <= 0 ? 2000 : requestTimeoutMs;
    }

    public long safeRemoteStateTtlMs() {
        return remoteStateTtlMs <= 0 ? 15000 : remoteStateTtlMs;
    }

    public int safeMaxAgentsPerNode() {
        return maxAgentsPerNode <= 0 ? 50 : maxAgentsPerNode;
    }


    public int safeMaxEventsPerNode() {
        return maxEventsPerNode <= 0 ? 50 : maxEventsPerNode;
    }
}

