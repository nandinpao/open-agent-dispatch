package com.opensocket.aievent.gateway.netty.delivery;

import com.opensocket.aievent.gateway.netty.admin.AdminEventMetricsRecorder;
import com.opensocket.aievent.gateway.netty.admin.AdminEventPublisher;
import com.opensocket.aievent.gateway.netty.agent.AgentRegistry;
import com.opensocket.aievent.gateway.netty.agent.AgentSnapshot;
import com.opensocket.aievent.gateway.netty.agent.AgentStatus;
import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.authorization.AgentAuthorizationRuntimeRegistry;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.protocol.AiEventEnvelope;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import com.opensocket.aievent.gateway.netty.protocol.TraceEnvelope;
import com.opensocket.aievent.gateway.netty.observability.AgentProtocolTraceService;
import com.opensocket.aievent.gateway.netty.tcp.TcpConnectionRegistry;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Netty transport component for command delivery. This service is deliberately limited to the
 * Core -> Netty -> Agent path. It does not create tasks, select agents, update task states, retry
 * business work, or call external tools.
 */
@Service
public class CommandDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(CommandDeliveryService.class);

    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;
    private final AgentRegistry agentRegistry;
    private final TcpConnectionRegistry tcpConnectionRegistry;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final AdminEventPublisher adminBroadcaster;
    private final AdminEventMetricsRecorder eventMetricsMeter;
    private final CommandDeliveryTracker deliveryTracker;
    private final AgentAuthorizationRuntimeRegistry authorizationRuntimeRegistry;
    private final AgentProtocolTraceService agentProtocolTraceService;

    @Autowired
    public CommandDeliveryService(
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties,
            AgentRegistry agentRegistry,
            TcpConnectionRegistry tcpConnectionRegistry,
            WebSocketSessionRegistry webSocketSessionRegistry,
            AdminEventPublisher adminBroadcaster,
            AdminEventMetricsRecorder eventMetricsMeter,
            CommandDeliveryTracker deliveryTracker,
            AgentAuthorizationRuntimeRegistry authorizationRuntimeRegistry,
            AgentProtocolTraceService agentProtocolTraceService
    ) {
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
        this.agentRegistry = agentRegistry;
        this.tcpConnectionRegistry = tcpConnectionRegistry;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.adminBroadcaster = adminBroadcaster;
        this.eventMetricsMeter = eventMetricsMeter;
        this.deliveryTracker = deliveryTracker;
        this.authorizationRuntimeRegistry = authorizationRuntimeRegistry;
        this.agentProtocolTraceService = agentProtocolTraceService == null ? AgentProtocolTraceService.noop() : agentProtocolTraceService;
    }

    public CommandDeliveryService(
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties,
            AgentRegistry agentRegistry,
            TcpConnectionRegistry tcpConnectionRegistry,
            WebSocketSessionRegistry webSocketSessionRegistry,
            AdminEventPublisher adminBroadcaster,
            AdminEventMetricsRecorder eventMetricsMeter,
            CommandDeliveryTracker deliveryTracker,
            AgentAuthorizationRuntimeRegistry authorizationRuntimeRegistry
    ) {
        this(objectMapper, gatewayProperties, agentRegistry, tcpConnectionRegistry, webSocketSessionRegistry,
                adminBroadcaster, eventMetricsMeter, deliveryTracker, authorizationRuntimeRegistry,
                AgentProtocolTraceService.noop());
    }

    public CommandDeliveryResponse deliverToAgent(String agentId, CommandDeliveryRequest request) {
        var safeRequest = request == null ? new CommandDeliveryRequest(null, null, Map.of(), null, null) : request;
        var commandId = normalizeCommandId(safeRequest.commandId());
        var issuedBy = blank(safeRequest.issuedBy()) ? gatewayProperties.nodeId() : safeRequest.issuedBy().trim();
        var dispatchContext = extractDispatchContext(safeRequest);
        var attempt = deliveryTracker.begin(
                commandId,
                safeRequest.traceId(),
                agentId,
                safeRequest.messageType(),
                issuedBy,
                dispatchContext.taskId(),
                dispatchContext.assignmentId(),
                dispatchContext.dispatchRequestId(),
                dispatchContext.attemptNo()
        );

        log.info("netty_command_delivery_started agentId={} commandId={} messageType={} taskId={} assignmentId={} dispatchRequestId={} attemptNo={}",
                safe(agentId), safe(commandId), safeRequest.messageType(), safe(dispatchContext.taskId()), safe(dispatchContext.assignmentId()),
                safe(dispatchContext.dispatchRequestId()), dispatchContext.attemptNo());

        if (blank(agentId)) {
            var response = complete(attempt, null, DeliveryStatus.INVALID_COMMAND, "agentId is required");
            eventMetricsMeter.recordFailed();
            broadcast(response);
            return response;
        }

        var dispatchContextError = validateTaskDispatchContext(agentId, safeRequest, dispatchContext);
        if (dispatchContextError != null) {
            var response = complete(attempt, null, DeliveryStatus.MISSING_DISPATCH_CONTEXT, dispatchContextError);
            eventMetricsMeter.recordFailed();
            broadcast(response);
            return response;
        }

        var agent = agentRegistry.findById(agentId);
        if (agent.isEmpty() || isNotConnected(agent.get())) {
            log.warn("netty_command_delivery_agent_not_connected agentId={} commandId={} taskId={} dispatchRequestId={} registryPresent={} status={}",
                    safe(agentId), safe(commandId), safe(dispatchContext.taskId()), safe(dispatchContext.dispatchRequestId()), agent.isPresent(), agent.map(AgentSnapshot::status).orElse(null));
            var response = complete(attempt, null, DeliveryStatus.AGENT_NOT_CONNECTED, "Agent is not connected to this gateway");
            eventMetricsMeter.recordFailed();
            broadcast(response);
            return response;
        }

        var snapshot = agent.get();
        if (!isAuthorizedForDelivery(snapshot)) {
            var response = complete(attempt, snapshot.connectionType(), DeliveryStatus.AGENT_NOT_AUTHORIZED, "Agent session is not authorized by Core");
            eventMetricsMeter.recordFailed();
            broadcast(response);
            return response;
        }
        var messageContext = AgentProtocolTraceService.MessageContext.of(
                snapshot.connectionType() == null ? "unknown" : snapshot.connectionType().name(),
                safeRequest.messageType(), MessageType.toDomainEventName(safeRequest.messageType()), commandId,
                agentId, firstNonBlank(snapshot.connectionId(), snapshot.sessionId()), dispatchContext.taskId());

        return agentProtocolTraceService.send(messageContext, trace -> {
            var envelope = new AiEventEnvelope<>(
                    commandId,
                    safeRequest.messageType(),
                    MessageType.toDomainEventName(safeRequest.messageType()),
                    issuedBy,
                    agentId,
                    OffsetDateTime.now(),
                    safeRequest.payload(),
                    trace
            );

            try {
                var json = serializeOutboundCommand(snapshot, commandId, issuedBy, safeRequest, envelope);
                var writeResult = writeToLocalTransport(snapshot, json, Duration.ofMillis(safeRequest.timeoutMs()));
                var status = toDeliveryStatus(writeResult.status());
                if (status == DeliveryStatus.DELIVERED) {
                    eventMetricsMeter.recordRouted();
                } else {
                    eventMetricsMeter.recordFailed();
                }
                var response = complete(attempt, snapshot.connectionType(), status, writeResult.message());
                log.info("netty_command_delivery_completed agentId={} commandId={} taskId={} dispatchRequestId={} connectionType={} deliveryStatus={} message={}",
                        safe(agentId), safe(commandId), safe(dispatchContext.taskId()), safe(dispatchContext.dispatchRequestId()), snapshot.connectionType(), status, safe(writeResult.message()));
                broadcast(response);
                return response;
            } catch (Exception ex) {
                eventMetricsMeter.recordFailed();
                log.warn("netty_command_delivery_exception agentId={} commandId={} taskId={} dispatchRequestId={} exception={} message={}",
                        safe(agentId), safe(commandId), safe(dispatchContext.taskId()), safe(dispatchContext.dispatchRequestId()), ex.getClass().getSimpleName(), ex.getMessage());
                var response = complete(attempt, snapshot.connectionType(), DeliveryStatus.DELIVERY_FAILED, ex.getMessage());
                broadcast(response);
                return response;
            }
        });
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public CommandDeliveryHistoryResponse history(int limit) {
        return deliveryTracker.historyResponse(limit);
    }

    public CommandDeliveryMetrics metrics() {
        return deliveryTracker.metrics();
    }

    private String validateTaskDispatchContext(String targetAgentId, CommandDeliveryRequest request, DispatchContext context) {
        if (request == null || request.messageType() != MessageType.TASK_DISPATCH) {
            return null;
        }
        if (blank(context.agentId())) {
            return "TASK_DISPATCH payload.agentId is required and must match the delivery path agentId";
        }
        if (!context.agentId().trim().equals(targetAgentId.trim())) {
            return "TASK_DISPATCH payload.agentId must match the delivery path agentId";
        }
        if (blank(context.taskId())) {
            return "TASK_DISPATCH payload.taskId is required";
        }
        if (blank(context.assignmentId())) {
            return "TASK_DISPATCH payload.assignmentId is required";
        }
        if (blank(context.dispatchRequestId())) {
            return "TASK_DISPATCH payload.dispatchRequestId is required";
        }
        if (blank(context.dispatchToken())) {
            return "TASK_DISPATCH payload.dispatchToken is required";
        }
        if (context.attemptNo() == null || context.attemptNo() <= 0) {
            return "TASK_DISPATCH payload.attemptNo or payload.attempt is required";
        }
        return null;
    }

    private DispatchContext extractDispatchContext(CommandDeliveryRequest request) {
        if (request == null || request.payload() == null) {
            return DispatchContext.empty();
        }
        var payload = request.payload();
        return new DispatchContext(
                trimToNull(stringValue(payload.get("taskId"))),
                trimToNull(firstNonBlank(
                        stringValue(payload.get("agentId")),
                        stringValue(payload.get("targetAgentId")),
                        stringValue(payload.get("selectedAgentId")))),
                trimToNull(stringValue(payload.get("assignmentId"))),
                trimToNull(stringValue(payload.get("dispatchRequestId"))),
                trimToNull(stringValue(payload.get("dispatchToken"))),
                parsePositiveAttempt(payload.get("attemptNo"), payload.get("attempt"))
        );
    }

    private Integer parsePositiveAttempt(Object attemptNo, Object attempt) {
        var parsedAttemptNo = parsePositiveInteger(attemptNo);
        if (parsedAttemptNo != null) {
            return parsedAttemptNo;
        }
        return parsePositiveInteger(attempt);
    }

    private Integer parsePositiveInteger(Object value) {
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        if (blank(stringValue(value))) {
            return null;
        }
        try {
            var parsed = Integer.parseInt(stringValue(value).trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String trimToNull(String value) {
        return blank(value) ? null : value.trim();
    }

    private String serializeOutboundCommand(
            AgentSnapshot snapshot,
            String commandId,
            String issuedBy,
            CommandDeliveryRequest request,
            AiEventEnvelope<Map<String, Object>> legacyEnvelope
    ) throws Exception {
        if (shouldUseOpenSocketProtocol(snapshot) && request.messageType() == MessageType.TASK_DISPATCH) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "event");
            event.put("id", commandId);
            event.put("event", "task.assign");
            event.put("timestamp", OffsetDateTime.now().toString());
            if (legacyEnvelope.trace() != null && legacyEnvelope.trace().present()) {
                event.put("trace", legacyEnvelope.trace());
            }
            event.put("payload", normalizeOpenSocketTaskAssignPayload(
                    snapshot, commandId, issuedBy, request.payload(), legacyEnvelope.trace()));
            return objectMapper.writeValueAsString(event);
        }
        return objectMapper.writeValueAsString(legacyEnvelope);
    }

    private Map<String, Object> normalizeOpenSocketTaskAssignPayload(
            AgentSnapshot snapshot,
            String commandId,
            String issuedBy,
            Map<String, Object> payload,
            TraceEnvelope trace
    ) {
        Map<String, Object> normalized = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        normalized.putIfAbsent("assignmentId", firstNonBlank(stringValue(normalized.get("assignmentId")), stringValue(normalized.get("dispatchRequestId")), "legacy:" + firstNonBlank(stringValue(normalized.get("taskId")), commandId)));
        normalized.putIfAbsent("attempt", normalizeAttempt(normalized.get("attempt"), normalized.get("attemptNo")));
        if (trace != null && trace.present()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            Object existingMetadata = normalized.get("metadata");
            if (existingMetadata instanceof Map<?, ?> existingMap) {
                existingMap.forEach((key, value) -> metadata.put(String.valueOf(key), value));
            }
            if (trace.traceparent() != null) {
                metadata.put("traceparent", trace.traceparent());
                normalized.put("traceparent", trace.traceparent());
            }
            if (trace.tracestate() != null) {
                metadata.put("tracestate", trace.tracestate());
                normalized.put("tracestate", trace.tracestate());
            }
            if (trace.baggage() != null) {
                metadata.put("baggage", trace.baggage());
                normalized.put("baggage", trace.baggage());
            }
            normalized.put("metadata", metadata);
        }
        Map<String, Object> routing = new LinkedHashMap<>();
        routing.put("routeId", commandId);
        routing.put("ownerNodeId", gatewayProperties.nodeId());
        routing.put("ownershipEpoch", 1);
        routing.put("sessionLeaseId", firstNonBlank(snapshot.sessionId(), snapshot.connectionId(), "local-session"));
        routing.put("dispatchedAt", OffsetDateTime.now().toString());
        normalized.putIfAbsent("routing", routing);
        Map<String, Object> gateway = new LinkedHashMap<>();
        gateway.put("issuedBy", issuedBy);
        gateway.put("gatewayNodeId", gatewayProperties.nodeId());
        gateway.put("connectionType", snapshot.connectionType() == null ? null : snapshot.connectionType().name());
        gateway.put("connectionId", snapshot.connectionId());
        gateway.put("sessionId", snapshot.sessionId());
        normalized.putIfAbsent("gateway", gateway);
        return normalized;
    }

    private boolean shouldUseOpenSocketProtocol(AgentSnapshot snapshot) {
        if (snapshot == null || snapshot.metadata() == null || snapshot.metadata().isEmpty()) {
            return false;
        }
        Object protocolFamily = snapshot.metadata().get("protocolFamily");
        if (protocolFamily != null && "OPENSOCKET_AGENT_PROTOCOL".equalsIgnoreCase(protocolFamily.toString())) {
            return true;
        }
        Object openSocket = snapshot.metadata().get("opensocketAgentProtocol");
        if (openSocket != null && Boolean.parseBoolean(openSocket.toString())) {
            return true;
        }
        Object pluginName = snapshot.metadata().get("pluginName");
        return pluginName != null && pluginName.toString().contains("openclaw-plugin-opensocket");
    }

    private Integer normalizeAttempt(Object attempt, Object attemptNo) {
        Object value = attempt == null ? attemptNo : attempt;
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (value != null) {
            try {
                return Math.max(1, Integer.parseInt(value.toString()));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        return 1;
    }

    private TransportWriteResult writeToLocalTransport(AgentSnapshot snapshot, String json, Duration timeout) {
        if (snapshot.connectionType() == ConnectionType.TCP) {
            return tcpConnectionRegistry.sendWithTimeout(snapshot.connectionId(), json + System.lineSeparator(), timeout);
        }
        if (snapshot.connectionType() == ConnectionType.WEBSOCKET) {
            return webSocketSessionRegistry.sendTextWithTimeout(snapshot.sessionId(), json, timeout);
        }
        return TransportWriteResult.notWritable("Agent connection type is not supported by this gateway");
    }

    private DeliveryStatus toDeliveryStatus(TransportWriteStatus status) {
        if (status == TransportWriteStatus.SENT) {
            return DeliveryStatus.DELIVERED;
        }
        if (status == TransportWriteStatus.NOT_WRITABLE) {
            return DeliveryStatus.CONNECTION_NOT_WRITABLE;
        }
        if (status == TransportWriteStatus.TIMEOUT) {
            return DeliveryStatus.DELIVERY_TIMEOUT;
        }
        return DeliveryStatus.DELIVERY_FAILED;
    }

    private boolean isNotConnected(AgentSnapshot agent) {
        return agent.status() == AgentStatus.OFFLINE
                || agent.status() == AgentStatus.TIMEOUT
                || (agent.connectionType() == ConnectionType.TCP && blank(agent.connectionId()))
                || (agent.connectionType() == ConnectionType.WEBSOCKET && blank(agent.sessionId()));
    }

    private boolean isAuthorizedForDelivery(AgentSnapshot agent) {
        if (authorizationRuntimeRegistry == null) {
            return true;
        }
        return authorizationRuntimeRegistry.isAuthorized(
                agent.connectionType(),
                agent.connectionId(),
                agent.sessionId(),
                agent.agentId()
        );
    }

    private CommandDeliveryResponse complete(
            CommandDeliveryTracker.DeliveryAttempt attempt,
            ConnectionType connectionType,
            DeliveryStatus status,
            String message
    ) {
        var record = deliveryTracker.complete(attempt, connectionType, status, message);
        return new CommandDeliveryResponse(
                record.attemptId(),
                record.commandId(),
                record.traceId(),
                record.agentId(),
                record.gatewayNodeId(),
                record.deliveryStatus(),
                record.connectionType(),
                record.requestedAt(),
                record.completedAt(),
                record.deliveryStatus() == DeliveryStatus.DELIVERED ? record.completedAt() : null,
                record.durationMillis(),
                record.message(),
                record.taskId(),
                record.assignmentId(),
                record.dispatchRequestId(),
                record.attemptNo()
        );
    }

    private void broadcast(CommandDeliveryResponse response) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("attemptId", response.attemptId());
        payload.put("commandId", response.commandId());
        payload.put("agentId", response.agentId());
        payload.put("gatewayNodeId", response.gatewayNodeId());
        payload.put("deliveryStatus", response.deliveryStatus().name());
        payload.put("connectionType", response.connectionType() == null ? "" : response.connectionType().name());
        payload.put("durationMillis", response.durationMillis());
        putIfPresent(payload, "taskId", response.taskId());
        putIfPresent(payload, "assignmentId", response.assignmentId());
        putIfPresent(payload, "dispatchRequestId", response.dispatchRequestId());
        if (response.attemptNo() != null) {
            payload.put("attemptNo", response.attemptNo());
        }
        adminBroadcaster.broadcast(
                "COMMAND_DELIVERY_" + response.deliveryStatus().name(),
                response.message(),
                payload
        );
    }


    private void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (!blank(value)) {
            payload.put(key, value);
        }
    }

    private record DispatchContext(
            String taskId,
            String agentId,
            String assignmentId,
            String dispatchRequestId,
            String dispatchToken,
            Integer attemptNo
    ) {
        static DispatchContext empty() {
            return new DispatchContext(null, null, null, null, null, null);
        }
    }

    private String normalizeCommandId(String commandId) {
        return blank(commandId) ? "cmd-" + UUID.randomUUID() : commandId.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
