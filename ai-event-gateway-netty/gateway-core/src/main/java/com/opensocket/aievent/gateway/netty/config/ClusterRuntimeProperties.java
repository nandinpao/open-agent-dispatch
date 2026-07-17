package com.opensocket.aievent.gateway.netty.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves cluster runtime settings from both bound Spring configuration properties and direct
 * environment variables.
 *
 * <p>This is intentionally defensive for Docker/VSCode/Jar-versioned deployments. In those
 * modes operators usually set variables such as {@code GATEWAY_CLUSTER_STATIC_PEERS} and
 * {@code GATEWAY_CLUSTER_ANNOUNCE_HOST}. If a profile or packaging path accidentally leaves the
 * nested {@code netty.cluster.*} record with safe defaults, this resolver still honors the direct
 * environment variables so cluster membership does not silently collapse to SELF only.</p>
 */
@Component
public class ClusterRuntimeProperties {

    private final NettyServerProperties nettyServerProperties;
    private final Environment environment;

    @Autowired
    public ClusterRuntimeProperties(NettyServerProperties nettyServerProperties, Environment environment) {
        this.nettyServerProperties = nettyServerProperties;
        this.environment = environment;
    }

    /** Constructor used by unit tests that instantiate core classes without Spring Environment. */
    public ClusterRuntimeProperties(NettyServerProperties nettyServerProperties) {
        this(nettyServerProperties, null);
    }

    public boolean enabled() {
        var value = env("GATEWAY_CLUSTER_ENABLED", "netty.cluster.enabled");
        if (!blank(value)) {
            return Boolean.parseBoolean(value.trim());
        }
        return cluster().enabled();
    }

    public String discoveryMode() {
        var value = env("GATEWAY_CLUSTER_DISCOVERY_MODE", "netty.cluster.discovery-mode");
        if (blank(value)) {
            value = cluster().safeDiscoveryMode();
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public boolean udpBroadcastEnabled() {
        var mode = discoveryMode();
        return "UDP_BROADCAST".equals(mode) || "HYBRID".equals(mode);
    }

    public boolean staticPeersEnabled() {
        var mode = discoveryMode();
        return "STATIC_PEERS".equals(mode) || "HYBRID".equals(mode);
    }

    public String staticPeersRaw() {
        var value = env("GATEWAY_CLUSTER_STATIC_PEERS", "netty.cluster.static-peers");
        if (!blank(value)) {
            return value.trim();
        }
        return blank(cluster().staticPeers()) ? "" : cluster().staticPeers().trim();
    }

    public List<NettyServerProperties.StaticPeer> parsedStaticPeers() {
        var raw = staticPeersRaw();
        if (blank(raw)) {
            return List.of();
        }
        var result = new ArrayList<NettyServerProperties.StaticPeer>();
        for (String item : raw.split(",")) {
            var peer = NettyServerProperties.StaticPeer.parse(item);
            if (peer != null) {
                result.add(peer);
            }
        }
        return List.copyOf(result);
    }

    public String announceHost() {
        var value = env("GATEWAY_CLUSTER_ANNOUNCE_HOST", "netty.cluster.announce-host");
        return blank(value) ? cluster().safeAnnounceHost() : value.trim();
    }

    public String udpHost() {
        var value = env("GATEWAY_CLUSTER_UDP_HOST", "netty.cluster.udp-host");
        return blank(value) ? cluster().safeUdpHost() : value.trim();
    }

    public int udpPort() {
        var value = env("GATEWAY_CLUSTER_UDP_PORT", "netty.cluster.udp-port");
        return parseInt(value, cluster().safeUdpPort());
    }

    public String broadcastHost() {
        var value = env("GATEWAY_CLUSTER_BROADCAST_HOST", "netty.cluster.broadcast-host");
        return blank(value) ? cluster().safeBroadcastHost() : value.trim();
    }

    public int broadcastPort() {
        var value = env("GATEWAY_CLUSTER_BROADCAST_PORT", "netty.cluster.broadcast-port");
        return parseInt(value, cluster().safeBroadcastPort());
    }

    public long heartbeatIntervalMs() {
        var value = env("GATEWAY_CLUSTER_HEARTBEAT_INTERVAL_MS", "netty.cluster.heartbeat-interval-ms");
        return parseLong(value, cluster().safeHeartbeatIntervalMs());
    }

    public long suspectTimeoutMs() {
        var value = env("GATEWAY_CLUSTER_SUSPECT_TIMEOUT_MS", "netty.cluster.suspect-timeout-ms");
        return parseLong(value, cluster().safeSuspectTimeoutMs());
    }

    public long offlineTimeoutMs() {
        var value = env("GATEWAY_CLUSTER_OFFLINE_TIMEOUT_MS", "netty.cluster.offline-timeout-ms");
        return parseLong(value, cluster().safeOfflineTimeoutMs());
    }

    public String internalToken() {
        var value = env("CLUSTER_INTERNAL_TOKEN", "netty.cluster.internal-token");
        return blank(value) ? cluster().safeInternalToken() : value.trim();
    }

    public int tcpPort() {
        var value = env("GATEWAY_TCP_PORT", "netty.tcp.port");
        return parseInt(value, nettyServerProperties.tcp().safePort());
    }

    public int websocketPort() {
        var value = env("GATEWAY_WS_PORT", "netty.websocket.port");
        return parseInt(value, nettyServerProperties.websocket().safePort());
    }

    private NettyServerProperties.Cluster cluster() {
        return nettyServerProperties.cluster() == null
                ? NettyServerProperties.Cluster.defaults()
                : nettyServerProperties.cluster();
    }

    private String env(String envName, String propertyName) {
        if (environment == null) {
            return null;
        }
        var direct = environment.getProperty(envName);
        if (!blank(direct)) {
            return direct;
        }
        return environment.getProperty(propertyName);
    }

    private static int parseInt(String raw, int fallback) {
        if (blank(raw)) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseLong(String raw, long fallback) {
        if (blank(raw)) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
