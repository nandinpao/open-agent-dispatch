package com.opensocket.aievent.gateway.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Configuration properties holder for Netty Server Properties. Values are bound from
 * application.yml and environment variables so local, Docker, cluster, and production deployments
 * can use the same code path.
 */
@ConfigurationProperties(prefix = "netty")
public record NettyServerProperties(
        Tcp tcp,
        Websocket websocket,
        Cluster cluster
) {
    /**
     * Spring Boot leaves a nested record property as null when no matching keys are present
     * in the active property sources. Keep this type null-safe because many tests and IDE
     * launch configurations intentionally provide only a subset of the netty.* tree.
     */
    public NettyServerProperties {
        tcp = tcp == null ? Tcp.defaults() : tcp;
        websocket = websocket == null ? Websocket.defaults() : websocket;
        cluster = cluster == null ? Cluster.defaults() : cluster;
    }

    public record Tcp(
            boolean enabled,
            String host,
            int port,
            int bossThreads,
            int workerThreads,
            int soBacklog,
            boolean soKeepalive,
            int maxFrameLengthBytes,
            long shutdownQuietPeriodMs,
            long shutdownTimeoutMs
    ) {
        /** Backward-compatible constructor kept for tests and manually created properties. */
        public Tcp(boolean enabled, String host, int port) {
            this(enabled, host, port, 1, 0, 128, true, 1024 * 1024, 100, 2000);
        }

        public static Tcp defaults() {
            return new Tcp(true, "0.0.0.0", 19090);
        }

        public String safeHost() {
            return blank(host) ? "0.0.0.0" : host.trim();
        }

        public int safePort() {
            return port < 0 ? 19090 : port;
        }

        public int safeBossThreads() {
            return bossThreads <= 0 ? 1 : bossThreads;
        }

        /** A value <= 0 delegates Netty worker sizing to the platform default. */
        public int safeWorkerThreads() {
            return workerThreads;
        }

        public int safeSoBacklog() {
            return soBacklog <= 0 ? 128 : soBacklog;
        }

        public int safeMaxFrameLengthBytes() {
            return maxFrameLengthBytes <= 0 ? 1024 * 1024 : maxFrameLengthBytes;
        }

        public long safeShutdownQuietPeriodMs() {
            return shutdownQuietPeriodMs < 0 ? 100 : shutdownQuietPeriodMs;
        }

        public long safeShutdownTimeoutMs() {
            return shutdownTimeoutMs <= 0 ? 2000 : shutdownTimeoutMs;
        }
    }

    public record Websocket(
            boolean enabled,
            String host,
            int port,
            int bossThreads,
            int workerThreads,
            int soBacklog,
            boolean soKeepalive,
            int maxContentLengthBytes,
            boolean allowExtensions,
            long shutdownQuietPeriodMs,
            long shutdownTimeoutMs
    ) {
        /** Backward-compatible constructor kept for tests and manually created properties. */
        public Websocket(boolean enabled, String host, int port) {
            this(enabled, host, port, 1, 0, 128, true, 1024 * 1024, true, 100, 2000);
        }

        public static Websocket defaults() {
            return new Websocket(true, "0.0.0.0", 19091);
        }

        public String safeHost() {
            return blank(host) ? "0.0.0.0" : host.trim();
        }

        public int safePort() {
            return port < 0 ? 19091 : port;
        }

        public int safeBossThreads() {
            return bossThreads <= 0 ? 1 : bossThreads;
        }

        /** A value <= 0 delegates Netty worker sizing to the platform default. */
        public int safeWorkerThreads() {
            return workerThreads;
        }

        public int safeSoBacklog() {
            return soBacklog <= 0 ? 128 : soBacklog;
        }

        public int safeMaxContentLengthBytes() {
            return maxContentLengthBytes <= 0 ? 1024 * 1024 : maxContentLengthBytes;
        }

        public boolean safeAllowExtensions() {
            return allowExtensions;
        }

        public long safeShutdownQuietPeriodMs() {
            return shutdownQuietPeriodMs < 0 ? 100 : shutdownQuietPeriodMs;
        }

        public long safeShutdownTimeoutMs() {
            return shutdownTimeoutMs <= 0 ? 2000 : shutdownTimeoutMs;
        }
    }

    public record Cluster(
            boolean enabled,
            String discoveryMode,
            String staticPeers,
            String udpHost,
            int udpPort,
            String broadcastHost,
            int broadcastPort,
            String announceHost,
            long heartbeatIntervalMs,
            long suspectTimeoutMs,
            long offlineTimeoutMs,
            String internalToken
    ) {
        /** Backward-compatible constructor kept for tests and manually created properties. */
        public Cluster(
                boolean enabled,
                String discoveryMode,
                String staticPeers,
                String udpHost,
                int udpPort,
                String broadcastHost,
                int broadcastPort,
                String announceHost,
                long heartbeatIntervalMs,
                long suspectTimeoutMs,
                long offlineTimeoutMs
        ) {
            this(enabled, discoveryMode, staticPeers, udpHost, udpPort, broadcastHost, broadcastPort,
                    announceHost, heartbeatIntervalMs, suspectTimeoutMs, offlineTimeoutMs, "");
        }

        public static Cluster defaults() {
            return new Cluster(false, "UDP_BROADCAST", "", "0.0.0.0", 19100,
                    "255.255.255.255", 19100, "127.0.0.1", 3000, 10000, 30000, "");
        }

        public String safeDiscoveryMode() {
            return blank(discoveryMode) ? "UDP_BROADCAST" : discoveryMode.trim().toUpperCase(Locale.ROOT);
        }

        public boolean udpBroadcastEnabled() {
            var mode = safeDiscoveryMode();
            return "UDP_BROADCAST".equals(mode) || "HYBRID".equals(mode);
        }

        public boolean staticPeersEnabled() {
            var mode = safeDiscoveryMode();
            return "STATIC_PEERS".equals(mode) || "HYBRID".equals(mode);
        }

        public List<StaticPeer> parsedStaticPeers() {
            if (blank(staticPeers)) {
                return List.of();
            }
            var result = new ArrayList<StaticPeer>();
            for (String raw : staticPeers.split(",")) {
                var peer = StaticPeer.parse(raw);
                if (peer != null) {
                    result.add(peer);
                }
            }
            return List.copyOf(result);
        }

        public String safeBroadcastHost() {
            return blank(broadcastHost) ? "255.255.255.255" : broadcastHost;
        }

        public int safeBroadcastPort() {
            return broadcastPort <= 0 ? safeUdpPort() : broadcastPort;
        }

        public String safeUdpHost() {
            return blank(udpHost) ? "0.0.0.0" : udpHost;
        }

        public int safeUdpPort() {
            return udpPort <= 0 ? 19100 : udpPort;
        }

        public String safeAnnounceHost() {
            return blank(announceHost) ? "127.0.0.1" : announceHost;
        }

        public long safeHeartbeatIntervalMs() {
            return heartbeatIntervalMs <= 0 ? 3000 : heartbeatIntervalMs;
        }

        public long safeSuspectTimeoutMs() {
            return suspectTimeoutMs <= 0 ? 10000 : suspectTimeoutMs;
        }

        public long safeOfflineTimeoutMs() {
            return offlineTimeoutMs <= 0 ? 30000 : offlineTimeoutMs;
        }

        public String safeInternalToken() {
            return blank(internalToken) ? "" : internalToken.trim();
        }
    }

    public record StaticPeer(
            String nodeId,
            String host,
            int adminPort,
            int tcpPort,
            int websocketPort,
            int clusterUdpPort
    ) {
        /**
         * Format: nodeId@host:adminPort:tcpPort:websocketPort:clusterUdpPort
         * Example: gateway-node-002@192.168.1.12:18080:19090:19091:19100
         *
         * <p>IPv6 hosts should be bracketed to avoid ambiguity:</p>
         *
         * <pre>gateway-node-002@[fd00::12]:18080:19090:19091:19100</pre>
         */
        public static StaticPeer parse(String raw) {
            if (raw == null || raw.isBlank() || !raw.contains("@")) {
                return null;
            }
            var parts = raw.trim().split("@", 2);
            var nodeId = parts[0].trim();
            var endpoint = parts[1].trim();
            if (nodeId.isBlank() || endpoint.isBlank()) {
                return null;
            }

            var parsed = parseEndpoint(endpoint);
            if (parsed == null || parsed.host().isBlank()) {
                return null;
            }

            return new StaticPeer(
                    nodeId,
                    parsed.host(),
                    parseInt(parsed.ports(), 0, 18080),
                    parseInt(parsed.ports(), 1, 19090),
                    parseInt(parsed.ports(), 2, 19091),
                    parseInt(parsed.ports(), 3, 19100)
            );
        }

        private static ParsedEndpoint parseEndpoint(String endpoint) {
            if (endpoint.startsWith("[")) {
                int end = endpoint.indexOf(']');
                if (end <= 1) {
                    return null;
                }
                var host = endpoint.substring(1, end).trim();
                var remainder = endpoint.substring(end + 1);
                if (remainder.startsWith(":")) {
                    remainder = remainder.substring(1);
                }
                return new ParsedEndpoint(host, remainder.isBlank() ? new String[0] : remainder.split(":"));
            }

            var segments = endpoint.split(":");
            if (segments.length == 1) {
                return new ParsedEndpoint(segments[0].trim(), new String[0]);
            }

            int portCount = Math.min(4, segments.length - 1);
            int hostEnd = segments.length - portCount;
            var host = String.join(":", List.of(segments).subList(0, hostEnd)).trim();
            var ports = new String[portCount];
            System.arraycopy(segments, hostEnd, ports, 0, portCount);
            return new ParsedEndpoint(host, ports);
        }

        private static int parseInt(String[] parts, int index, int fallback) {
            if (parts.length <= index || parts[index] == null || parts[index].isBlank()) {
                return fallback;
            }
            try {
                return Integer.parseInt(parts[index].trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private record ParsedEndpoint(String host, String[] ports) {
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
