package com.opensocket.aievent.gateway.netty.directory;

import com.opensocket.aievent.gateway.netty.agent.AgentSnapshot;
import com.opensocket.aievent.gateway.netty.agent.AgentStatus;
import com.opensocket.aievent.gateway.netty.agent.AgentRegistry;
import com.opensocket.aievent.gateway.netty.authorization.AgentAuthorizationRuntimeRegistry;
import com.opensocket.aievent.gateway.netty.config.CoreDirectorySyncProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.config.NettyServerProperties;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundDispatcher;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundRequest;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * HTTP publisher from Netty's local runtime view into Core's Global Agent Directory.
 *
 * <p>All requests are submitted to a bounded Core outbound worker so TCP/WebSocket event-loop
 * processing is not blocked by Core availability or latency. Failures are logged and corrected by
 * the next heartbeat or snapshot reconciliation cycle.</p>
 */
@Service
public class CoreDirectorySyncService implements CoreDirectorySyncPublisher {
    private static final Logger log = LoggerFactory.getLogger(CoreDirectorySyncService.class);
    private static final Duration AGENT_DIRECTORY_RECONCILE_COOLDOWN = Duration.ofSeconds(10);

    private final CoreDirectorySyncProperties properties;
    private final GatewayProperties gatewayProperties;
    private final NettyServerProperties nettyServerProperties;
    private final AgentRegistry agentRegistry;
    private final AgentAuthorizationRuntimeRegistry authorizationRuntimeRegistry;
    private final ObjectMapper objectMapper;
    private final CoreOutboundDispatcher coreOutboundDispatcher;
    private final Environment environment;
    private final Map<String, Long> agentDirectoryReconcileAttempts = new ConcurrentHashMap<>();
    private volatile long applicationReadyNanos;

    @Autowired
    public CoreDirectorySyncService(
            CoreDirectorySyncProperties properties,
            GatewayProperties gatewayProperties,
            NettyServerProperties nettyServerProperties,
            AgentRegistry agentRegistry,
            AgentAuthorizationRuntimeRegistry authorizationRuntimeRegistry,
            ObjectMapper objectMapper,
            Environment environment,
            CoreOutboundDispatcher coreOutboundDispatcher
    ) {
        this.properties = properties;
        this.gatewayProperties = gatewayProperties;
        this.nettyServerProperties = nettyServerProperties;
        this.agentRegistry = agentRegistry;
        this.authorizationRuntimeRegistry = authorizationRuntimeRegistry;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.coreOutboundDispatcher = coreOutboundDispatcher;
    }

    CoreDirectorySyncService(
            CoreDirectorySyncProperties properties,
            GatewayProperties gatewayProperties,
            NettyServerProperties nettyServerProperties,
            AgentRegistry agentRegistry,
            ObjectMapper objectMapper,
            Environment environment,
            CoreOutboundDispatcher coreOutboundDispatcher
    ) {
        this(properties, gatewayProperties, nettyServerProperties, agentRegistry, null,
                objectMapper, environment, coreOutboundDispatcher);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        applicationReadyNanos = System.nanoTime();
        if (!properties.enabled() || !properties.registerOnStartup()) {
            return;
        }
        publishGatewayRegistration();
        publishGatewayHeartbeat();
        publishGatewaySnapshot(agentRegistry.list());
    }

    @Scheduled(fixedDelayString = "${gateway.core-directory-sync.gateway-heartbeat-interval-ms:15000}")
    public void scheduledGatewayHeartbeat() {
        publishGatewayHeartbeat();
    }

    @Scheduled(fixedDelayString = "${gateway.core-directory-sync.snapshot-interval-ms:60000}")
    public void scheduledGatewaySnapshot() {
        publishGatewaySnapshot(agentRegistry.list());
    }

    @Override
    public void publishGatewayRegistration() {
        if (!properties.enabled()) {
            return;
        }
        post(properties.gatewayRegisterUrl(), gatewayNodePayload(), "gateway registration");
    }

    @Override
    public void publishGatewayHeartbeat() {
        if (!properties.enabled()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ONLINE");
        payload.put("leaseTtlSeconds", properties.gatewayLeaseTtlSeconds());
        post(properties.gatewayHeartbeatUrl(gatewayProperties.nodeId()), payload, "gateway heartbeat");
    }

    @Override
    public void publishAgentConnected(AgentSnapshot agent) {
        if (!properties.enabled() || agent == null || blank(agent.agentId())) {
            return;
        }
        // Discovery-only Agents must stay local to Netty until Core authorization provides
        // an authoritative tenant/profile. This guards the single-agent sync path in addition
        // to the periodic snapshot filter.
        if (!eligibleForCoreDirectorySync(agent)) {
            return;
        }
        post(properties.agentConnectedUrl(gatewayProperties.nodeId(), agent.agentId()), agentPayload(agent), "agent connected");
    }

    @Override
    public void publishAgentHeartbeat(AgentSnapshot agent) {
        if (!properties.enabled() || agent == null || blank(agent.agentId())) {
            return;
        }
        // Defense in depth: transport-only/discovery Agents are intentionally absent from Core's
        // tenant-scoped runtime directory. Never send their heartbeats to Core.
        if (!eligibleForCoreDirectorySync(agent)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", toCoreAgentStatus(agent.status()));
        payload.put("currentTaskCount", currentTaskCount(agent));
        payload.put("healthScore", healthScore(agent));
        payload.put("agentSessionId", agentSessionId(agent));
        Map<String, Object> runtimeLoad = runtimeLoadForCore(agent);
        if (!runtimeLoad.isEmpty()) {
            payload.put("runtimeLoad", runtimeLoad);
        }
        putIfPresent(payload, "capabilityRevision", metadataText(agent, "capabilityRevision"));
        Object plugin = agent.metadata() == null ? null : agent.metadata().get("plugin");
        if (plugin != null) {
            payload.put("plugin", plugin);
        }
        Object capabilityProfile = agent.metadata() == null ? null : agent.metadata().get("capabilityProfile");
        if (capabilityProfile != null) {
            payload.put("capabilityProfile", capabilityProfile);
        }
        post(properties.agentHeartbeatUrl(gatewayProperties.nodeId(), agent.agentId()), payload, "agent heartbeat", result -> {
            if (result.success2xx()) {
                agentDirectoryReconcileAttempts.remove(agent.agentId());
                return;
            }
            if (coreDirectoryMissingAgent(result) && eligibleForCoreDirectorySync(agent) && claimDirectoryReconcile(agent.agentId())) {
                log.info("Core directory is missing an already authorized Agent. Replaying governed connected snapshot. agentId={}, gatewayNodeId={}",
                        agent.agentId(), gatewayProperties.nodeId());
                publishAgentConnected(agent);
            }
        });
    }

    @Override
    public void publishAgentDisconnected(AgentSnapshot agent, String reason) {
        if (!properties.enabled() || agent == null || blank(agent.agentId())) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentSessionId", agentSessionId(agent));
        payload.put("reason", blank(reason) ? "netty transport disconnected" : reason);
        post(properties.agentDisconnectedUrl(gatewayProperties.nodeId(), agent.agentId()), payload, "agent disconnected");
    }

    @Override
    public void publishGatewaySnapshot(List<AgentSnapshot> agents) {
        if (!properties.enabled()) {
            return;
        }
        List<Map<String, Object>> agentPayloads = agents == null ? List.of() : agents.stream()
                .filter(agent -> agent != null && !blank(agent.agentId()))
                .filter(agent -> agent.status() != AgentStatus.OFFLINE && agent.status() != AgentStatus.TIMEOUT)
                // Core Global Agent Directory is tenant-scoped authority/runtime state.
                // Discovery-only Agents deliberately remain in Netty AgentRegistry until Core
                // authorization succeeds and supplies an authoritative Tenant. Admin UI observes
                // those candidates directly through /api/admin/runtime/agents.
                .filter(this::eligibleForCoreDirectorySync)
                .map(this::agentPayload)
                .toList();
        Map<String, Object> payload = Map.of("agents", agentPayloads);
        post(properties.gatewaySnapshotUrl(gatewayProperties.nodeId()), payload, "gateway agent snapshot");
    }


    private boolean eligibleForCoreDirectorySync(AgentSnapshot agent) {
        if (agent == null || blank(agent.agentId())) {
            return false;
        }
        // Compatibility constructor is retained for focused unit tests and legacy embedding.
        // Normal Spring runtime always injects the authorization registry.
        if (authorizationRuntimeRegistry == null) {
            return true;
        }
        var context = authorizationRuntimeRegistry.findByAgentId(agent.agentId()).orElse(null);
        if (!authorizationRuntimeRegistry.isAuthorizedAgent(context) || blank(context.tenantId())) {
            return false;
        }
        return authorizationRuntimeRegistry.isAuthorized(
                agent.connectionType(),
                agent.connectionId(),
                agent.sessionId(),
                agent.agentId()
        );
    }

    private Map<String, Object> gatewayNodePayload() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("environment", gatewayProperties.environment());
        metadata.put("description", gatewayProperties.description());
        metadata.put("tcpEnabled", nettyServerProperties.tcp().enabled());
        metadata.put("websocketEnabled", nettyServerProperties.websocket().enabled());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gatewayNodeId", gatewayProperties.nodeId());
        payload.put("nodeName", gatewayProperties.nodeId());
        payload.put("hostName", environment.getProperty("HOSTNAME", "localhost"));
        payload.put("advertiseHost", nettyServerProperties.cluster().safeAnnounceHost());
        payload.put("httpPort", httpPort());
        payload.put("wsPort", nettyServerProperties.websocket().safePort());
        payload.put("region", gatewayProperties.region());
        payload.put("zone", gatewayProperties.zone());
        payload.put("siteId", gatewayProperties.siteId());
        payload.put("status", "ONLINE");
        payload.put("version", gatewayProperties.version());
        payload.put("metadata", metadata);
        payload.put("leaseExpiresAt", iso(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(properties.gatewayLeaseTtlSeconds())));
        return payload;
    }

    private Map<String, Object> agentPayload(AgentSnapshot agent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", agent.agentId());
        String tenantId = tenantId(agent);
        if (!blank(tenantId)) {
            payload.put("tenantId", tenantId);
        }
        payload.put("agentType", agent.agentType() == null ? "CUSTOM" : agent.agentType().name());
        payload.put("ownerGatewayNodeId", gatewayProperties.nodeId());
        payload.put("agentSessionId", agentSessionId(agent));
        payload.put("siteId", gatewayProperties.siteId());
        payload.put("siteName", gatewayProperties.siteName());
        payload.put("region", gatewayProperties.region());
        payload.put("zone", gatewayProperties.zone());
        payload.put("status", toCoreAgentStatus(agent.status()));
        payload.put("capabilities", agent.capabilities() == null ? List.of() : agent.capabilities());
        payload.put("currentTaskCount", currentTaskCount(agent));
        payload.put("reservedTaskCount", 0);
        payload.put("maxConcurrentTasks", maxConcurrentTasks(agent));
        payload.put("healthScore", healthScore(agent));
        Map<String, Object> runtimeLoad = runtimeLoadForCore(agent);
        if (!runtimeLoad.isEmpty()) {
            payload.put("runtimeLoad", runtimeLoad);
        }
        putIfPresent(payload, "capabilityRevision", metadataText(agent, "capabilityRevision"));
        putIfPresent(payload, "pluginName", metadataText(agent, "pluginName"));
        putIfPresent(payload, "pluginVersion", metadataText(agent, "pluginVersion"));
        Object capabilityProfile = agent.metadata() == null ? null : agent.metadata().get("capabilityProfile");
        if (capabilityProfile != null) {
            payload.put("capabilityProfile", capabilityProfile);
        }
        Object plugin = agent.metadata() == null ? null : agent.metadata().get("plugin");
        if (plugin != null) {
            payload.put("plugin", plugin);
        }
        payload.put("connectedAt", iso(agent.registeredAt()));
        payload.put("lastHeartbeatAt", iso(agent.lastHeartbeatAt()));
        payload.put("leaseExpiresAt", iso(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(properties.agentLeaseTtlSeconds())));
        return payload;
    }

    private String tenantId(AgentSnapshot agent) {
        if (agent == null || blank(agent.agentId())) {
            return null;
        }
        if (authorizationRuntimeRegistry != null) {
            String authorizedTenant = authorizationRuntimeRegistry.findByAgentId(agent.agentId())
                    .map(context -> context.tenantId())
                    .orElse(null);
            if (!blank(authorizedTenant)) {
                return authorizedTenant;
            }
        }
        return metadataText(agent, "tenantId");
    }

    private void post(String url, Object payload, String operation) {
        post(url, payload, operation, null);
    }

    private void post(String url, Object payload, String operation, Consumer<com.opensocket.aievent.gateway.netty.outbound.CoreOutboundResult> completion) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            Map<String, String> headers = new LinkedHashMap<>();
            if (properties.hasAuthToken()) {
                headers.put(properties.authHeaderName(), properties.authToken());
            }
            var submission = coreOutboundDispatcher.submit(
                    "directory sync " + operation,
                    CoreOutboundRequest.jsonPost(URI.create(url), body, headers),
                    result -> {
                        if (!result.success2xx()) {
                            if (result.status() == CoreOutboundStatus.HTTP_ERROR) {
                                log.warn("Core directory sync failed. operation={}, status={}, body={}",
                                        operation, result.httpStatus(), truncate(result.responseBody()));
                            } else if (withinStartupFailureGrace()) {
                                log.info("Core directory sync startup pending. operation={}, status={}, reason={}",
                                        operation, result.status(), result.message());
                            } else {
                                log.warn("Core directory sync request failed. operation={}, status={}, reason={}",
                                        operation, result.status(), result.message());
                            }
                        }
                        if (completion != null) {
                            completion.accept(result);
                        }
                    }
            );
            if (!submission.accepted()) {
                if (withinStartupFailureGrace()) {
                    log.info("Core directory sync startup submission pending. operation={}, status={}, reason={}",
                            operation, submission.status(), submission.message());
                } else {
                    log.warn("Core directory sync request rejected before HTTP execution. operation={}, status={}, reason={}",
                            operation, submission.status(), submission.message());
                }
            }
        } catch (Exception ex) {
            log.warn("Core directory sync request could not be created. operation={}, reason={}", operation, ex.getMessage());
        }
    }

    private boolean withinStartupFailureGrace() {
        long started = applicationReadyNanos;
        long graceMs = properties.startupFailureGraceMs();
        if (started == 0L || graceMs <= 0L) {
            return false;
        }
        return System.nanoTime() - started <= java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(graceMs);
    }

    private boolean coreDirectoryMissingAgent(com.opensocket.aievent.gateway.netty.outbound.CoreOutboundResult result) {
        if (result == null || result.status() != CoreOutboundStatus.HTTP_ERROR) {
            return false;
        }
        if (result.httpStatus() != 400 && result.httpStatus() != 404) {
            return false;
        }
        String body = result.responseBody();
        return body != null && body.toLowerCase(java.util.Locale.ROOT).contains("agent not found");
    }

    private boolean claimDirectoryReconcile(String agentId) {
        if (blank(agentId)) {
            return false;
        }
        long now = System.nanoTime();
        long cooldown = AGENT_DIRECTORY_RECONCILE_COOLDOWN.toNanos();
        while (true) {
            Long previous = agentDirectoryReconcileAttempts.get(agentId);
            if (previous != null && now - previous < cooldown) {
                return false;
            }
            if (previous == null) {
                if (agentDirectoryReconcileAttempts.putIfAbsent(agentId, now) == null) {
                    return true;
                }
            } else if (agentDirectoryReconcileAttempts.replace(agentId, previous, now)) {
                return true;
            }
        }
    }

    private int httpPort() {
        return environment.getProperty("server.port", Integer.class, 18080);
    }

    private String agentSessionId(AgentSnapshot agent) {
        if (!blank(agent.sessionId())) {
            return agent.sessionId();
        }
        if (!blank(agent.connectionId())) {
            return agent.connectionId();
        }
        return agent.agentId() + "@" + gatewayProperties.nodeId();
    }

    private int currentTaskCount(AgentSnapshot agent) {
        Map<String, Object> runtimeLoad = runtimeLoad(agent);
        Integer activeTasks = intValue(runtimeLoad.get("activeTasks"));
        if (activeTasks != null) {
            return Math.max(0, activeTasks);
        }
        if (!blank(agent.currentTaskId())) {
            return 1;
        }
        return agent.status() == AgentStatus.BUSY ? 1 : 0;
    }

    private int maxConcurrentTasks(AgentSnapshot agent) {
        Map<String, Object> runtimeLoad = runtimeLoad(agent);
        Integer fromRuntimeLoad = intValue(runtimeLoad.get("maxConcurrentTasks"));
        if (fromRuntimeLoad != null) {
            return Math.max(1, fromRuntimeLoad);
        }
        String value = metadataText(agent, "maxConcurrentTasks");
        if (!blank(value)) {
            try {
                return Math.max(1, Integer.parseInt(value.trim()));
            } catch (NumberFormatException ignored) {
                // Fall back to configured default.
            }
        }
        return properties.defaultAgentMaxConcurrentTasks();
    }

    private int healthScore(AgentSnapshot agent) {
        Map<String, Object> runtimeLoad = runtimeLoad(agent);
        if (Boolean.TRUE.equals(boolValue(runtimeLoad.get("draining"))) || agent.status() == AgentStatus.DRAINING) {
            return 0;
        }
        Number utilization = numberValue(runtimeLoad.get("capacityUtilization"));
        if (utilization != null) {
            double value = Math.max(0.0d, Math.min(1.0d, utilization.doubleValue()));
            return (int) Math.round(100.0d - (value * 50.0d));
        }
        return properties.defaultAgentHealthScore();
    }

    private Map<String, Object> runtimeLoadForCore(AgentSnapshot agent) {
        Map<String, Object> result = new LinkedHashMap<>(runtimeLoad(agent));
        if (agent != null && agent.connectionType() != null) {
            result.put("connectionType", agent.connectionType().name());
        }
        String protocolVersion = metadataText(agent, "protocolVersion");
        if (!blank(protocolVersion)) {
            result.put("protocolVersion", protocolVersion);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runtimeLoad(AgentSnapshot agent) {
        if (agent == null || agent.metadata() == null) {
            return Map.of();
        }
        Object value = agent.metadata().get("runtimeLoad");
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private String metadataText(AgentSnapshot agent, String key) {
        if (agent == null || agent.metadata() == null || key == null) {
            return null;
        }
        Object value = agent.metadata().get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private Integer intValue(Object value) {
        Number number = numberValue(value);
        return number == null ? null : number.intValue();
    }

    private Number numberValue(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean boolValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (!blank(value)) {
            payload.put(key, value);
        }
    }

    private String toCoreAgentStatus(AgentStatus status) {
        if (status == null) {
            return "IDLE";
        }
        return switch (status) {
            case CONNECTED, ONLINE, IDLE -> "IDLE";
            case BUSY -> "BUSY";
            case DRAINING -> "DRAINING";
            case DEGRADED, ERROR -> "ERROR";
            case DISCONNECTED, OFFLINE -> "OFFLINE";
            case TIMEOUT -> "EXPIRED";
        };
    }

    private String iso(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
