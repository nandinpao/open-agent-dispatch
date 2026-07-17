package com.opensocket.aievent.gateway.netty.websocket;

import com.opensocket.aievent.gateway.netty.admin.AdminEventMetricsRecorder;
import com.opensocket.aievent.gateway.netty.agent.AgentLifecycleService;
import com.opensocket.aievent.gateway.netty.agent.AgentType;
import com.opensocket.aievent.gateway.netty.agent.AgentOnboardingTokenValidator;
import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.authorization.AgentAuthorizationDeniedException;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentHeartbeatPayload;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentRegisterPayload;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentStatusChangePayload;
import com.opensocket.aievent.gateway.netty.common.dto.ErrorPayload;
import com.opensocket.aievent.gateway.netty.common.dto.GatewayAckPayload;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.config.TaskAssignmentProperties;
import com.opensocket.aievent.gateway.netty.callback.TaskCallbackRelay;
import com.opensocket.aievent.gateway.netty.callback.TaskCallbackRelayResult;
import com.opensocket.aievent.gateway.netty.inbound.InboundEventForwarder;
import com.opensocket.aievent.gateway.netty.inbound.InboundEventRecord;
import com.opensocket.aievent.gateway.netty.inbound.InboundForwardStatus;
import com.opensocket.aievent.gateway.netty.protocol.AiEventEnvelope;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import com.opensocket.aievent.gateway.netty.protocol.TraceEnvelope;
import com.opensocket.aievent.gateway.netty.observability.AgentProtocolTraceService;
import com.opensocket.aievent.gateway.netty.task.dto.TaskAckPayload;
import com.opensocket.aievent.gateway.netty.task.dto.TaskErrorPayload;
import com.opensocket.aievent.gateway.netty.task.dto.TaskProgressPayload;
import com.opensocket.aievent.gateway.netty.task.dto.TaskResultPayload;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WebSocket transport processor. It supports Agent and Admin UI real-time channels, validates
 * inbound Agent messages, and updates only local connection state. Task lifecycle decisions belong
 * to ai-event-gateway-core / control-plane, not this Netty transport gateway.
 */
@Component
public class WebSocketMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketMessageProcessor.class);

    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;
    private final WebSocketSessionRegistry sessionRegistry;
    private final AgentLifecycleService agentLifecycleService;
    private final AdminEventMetricsRecorder eventMetricsMeter;
    private final AgentOnboardingTokenValidator agentOnboardingTokenValidator;
    private final InboundEventForwarder inboundEventForwarder;
    private final TaskCallbackRelay taskCallbackRelay;
    private final TaskAssignmentProperties taskAssignmentProperties;
    private final AgentProtocolTraceService agentProtocolTraceService;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Autowired
    public WebSocketMessageProcessor(
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties,
            WebSocketSessionRegistry sessionRegistry,
            AgentLifecycleService agentLifecycleService,
            AdminEventMetricsRecorder eventMetricsMeter,
            AgentOnboardingTokenValidator agentOnboardingTokenValidator,
            InboundEventForwarder inboundEventForwarder,
            TaskCallbackRelay taskCallbackRelay,
            TaskAssignmentProperties taskAssignmentProperties,
            AgentProtocolTraceService agentProtocolTraceService
    ) {
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
        this.sessionRegistry = sessionRegistry;
        this.agentLifecycleService = agentLifecycleService;
        this.eventMetricsMeter = eventMetricsMeter;
        this.agentOnboardingTokenValidator = agentOnboardingTokenValidator;
        this.inboundEventForwarder = inboundEventForwarder;
        this.taskCallbackRelay = taskCallbackRelay;
        this.taskAssignmentProperties = taskAssignmentProperties == null ? new TaskAssignmentProperties() : taskAssignmentProperties;
        this.agentProtocolTraceService = agentProtocolTraceService == null ? AgentProtocolTraceService.noop() : agentProtocolTraceService;
    }

    public WebSocketMessageProcessor(
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties,
            WebSocketSessionRegistry sessionRegistry,
            AgentLifecycleService agentLifecycleService,
            AdminEventMetricsRecorder eventMetricsMeter,
            AgentOnboardingTokenValidator agentOnboardingTokenValidator,
            InboundEventForwarder inboundEventForwarder,
            TaskCallbackRelay taskCallbackRelay,
            TaskAssignmentProperties taskAssignmentProperties
    ) {
        this(objectMapper, gatewayProperties, sessionRegistry, agentLifecycleService, eventMetricsMeter,
                agentOnboardingTokenValidator, inboundEventForwarder, taskCallbackRelay, taskAssignmentProperties,
                AgentProtocolTraceService.noop());
    }

    public String processText(String sessionId, WebSocketClientType clientType, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> rawEnvelope = tryReadObject(text);
            if (isOpenSocketEnvelope(rawEnvelope)) {
                String event = firstNonBlank(textValue(rawEnvelope.get("event")), textValue(rawEnvelope.get("method")), textValue(rawEnvelope.get("type")));
                String agentId = sessionRegistry.getAgentId(sessionId);
                var receiveContext = AgentProtocolTraceService.MessageContext.of(
                        "websocket", null, event, textValue(rawEnvelope.get("id")), agentId, sessionId, openSocketTaskId(rawEnvelope));
                return agentProtocolTraceService.receive(TraceEnvelope.fromMap(rawEnvelope), receiveContext, () -> {
                    String response = processOpenSocketEnvelope(sessionId, clientType, rawEnvelope, text);
                    if (response == null) {
                        return null;
                    }
                    var sendContext = AgentProtocolTraceService.MessageContext.of(
                            "websocket", null, event + ".ack", textValue(rawEnvelope.get("id")), agentId, sessionId, openSocketTaskId(rawEnvelope));
                    return agentProtocolTraceService.send(sendContext, trace -> addTraceToJson(response, trace));
                });
            }

            var inbound = objectMapper.readValue(
                    text,
                    new TypeReference<AiEventEnvelope<JsonNode>>() {
                    }
            );
            String agentId = sessionRegistry.getAgentId(sessionId);
            var receiveContext = AgentProtocolTraceService.MessageContext.of(
                    "websocket", inbound.messageType(), inbound.eventType(), inbound.messageId(),
                    agentId, sessionId, taskId(inbound.payload()));
            return agentProtocolTraceService.receive(inbound.trace(), receiveContext,
                    () -> processLegacyEnvelope(sessionId, clientType, text, inbound));
        } catch (AssignmentBoundaryViolationException ex) {
            eventMetricsMeter.recordFailed();
            log.warn("Rejected WebSocket task assignment boundary violation. sessionId={}, reason={}", sessionId, ex.getMessage());
            return toJson(error(ex.code(), ex.userMessage(), ex.getMessage()));
        } catch (UnsupportedMessageTypeException ex) {
            eventMetricsMeter.recordFailed();
            log.warn("Unsupported WebSocket message type. sessionId={}, reason={}", sessionId, ex.getMessage());
            return toJson(error("UNSUPPORTED_MESSAGE_TYPE", "Unsupported WebSocket message type", ex.getMessage()));
        } catch (AgentAuthorizationDeniedException ex) {
            eventMetricsMeter.recordFailed();
            log.warn("Agent authorization denied. reason={}", ex.getMessage());
            return toJson(error(ex.code(), "Agent was not authorized by ai-event-gateway-core", ex.getMessage()));
        } catch (PayloadValidationException ex) {
            eventMetricsMeter.recordFailed();
            log.warn("Invalid WebSocket payload. sessionId={}, reason={}", sessionId, ex.getMessage());
            return toJson(error("INVALID_PAYLOAD", "Protocol payload validation failed", ex.getMessage()));
        } catch (Exception ex) {
            eventMetricsMeter.recordFailed();
            if (ex.getMessage() != null && ex.getMessage().contains("Unsupported messageType/eventType")) {
                log.warn("Unsupported WebSocket message type. sessionId={}, reason={}", sessionId, ex.getMessage());
                return toJson(error("UNSUPPORTED_MESSAGE_TYPE", "Unsupported WebSocket message type", ex.getMessage()));
            }
            log.warn("Failed to process WebSocket message. sessionId={}, reason={}", sessionId, ex.getMessage());
            return toJson(error("INVALID_JSON", "Invalid WebSocket JSON message", ex.getMessage()));
        }
    }

    private String processLegacyEnvelope(
            String sessionId, WebSocketClientType clientType, String text, AiEventEnvelope<JsonNode> inbound) {
        if (inbound.messageType() == null) {
            eventMetricsMeter.recordFailed();
            return toJson(error("INVALID_MESSAGE_TYPE", "messageType is required", text));
        }

        eventMetricsMeter.recordInbound();
        handleTransportSideEffects(sessionId, clientType, inbound);
        TaskCallbackRelayResult callbackRelayResult = null;
        InboundEventRecord inboundForwardRecord = null;
        if (taskCallbackRelay.isTaskCallback(inbound.messageType())) {
            callbackRelayResult = taskCallbackRelay.accept(
                    inbound, ConnectionType.WEBSOCKET, sessionId, sessionRegistry.getAgentId(sessionId));
        } else {
            inboundForwardRecord = inboundEventForwarder.accept(
                    inbound, ConnectionType.WEBSOCKET, sessionId, sessionRegistry.getAgentId(sessionId));
        }

        var ack = AiEventEnvelope.of(
                MessageType.GATEWAY_ACK, gatewayProperties.nodeId(), inbound.source(),
                new GatewayAckPayload(
                        inbound.messageId(), inbound.messageType().name(), sessionId,
                        gatewayAckStatus(callbackRelayResult, inboundForwardRecord),
                        gatewayAckMessage(callbackRelayResult, inboundForwardRecord, "WebSocket")));
        var sendContext = AgentProtocolTraceService.MessageContext.of(
                "websocket", MessageType.GATEWAY_ACK, "gateway.ack", ack.messageId(),
                sessionRegistry.getAgentId(sessionId), sessionId, taskId(inbound.payload()));
        return agentProtocolTraceService.send(sendContext, trace -> toJson(ack.withTrace(trace)));
    }

    private String addTraceToJson(String json, TraceEnvelope trace) {
        if (json == null || trace == null || !trace.present()) {
            return json;
        }
        try {
            Map<String, Object> response = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            response.put("trace", trace);
            return objectMapper.writeValueAsString(response);
        } catch (Exception ignored) {
            return json;
        }
    }

    private String openSocketTaskId(Map<String, Object> envelope) {
        Map<String, Object> payload = asMap(envelope == null ? null : envelope.get("payload"));
        return textValue(payload.get("taskId"));
    }

    private String taskId(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        JsonNode taskId = payload.get("taskId");
        return taskId == null || taskId.isNull() ? null : taskId.asText();
    }

    private Map<String, Object> tryReadObject(String text) {
        try {
            return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private boolean isOpenSocketEnvelope(Map<String, Object> envelope) {
        if (envelope == null || envelope.isEmpty()) {
            return false;
        }
        Object type = envelope.get("type");
        if (type == null) {
            return false;
        }
        return envelope.containsKey("method") || envelope.containsKey("event") || envelope.containsKey("params");
    }

    @SuppressWarnings("unchecked")
    private String processOpenSocketEnvelope(String sessionId, WebSocketClientType clientType, Map<String, Object> envelope, String rawText) {
        if (clientType != WebSocketClientType.AGENT) {
            return toJson(openSocketError(envelope.get("id"), "UNSUPPORTED_CLIENT_TYPE", "WebSocket client type is receive-only for OpenSocket Agent protocol messages", false));
        }
        String type = textValue(envelope.get("type"));
        String method = textValue(envelope.get("method"));
        String event = textValue(envelope.get("event"));
        try {
            if ("req".equals(type) && "agent.connect".equals(method)) {
                Map<String, Object> params = asMap(envelope.get("params"));
                AgentRegisterPayload payload = toAgentRegisterPayload(params);
                agentOnboardingTokenValidator.validationFailure(payload, sessionRegistry.isAgentOnboardingAuthenticated(sessionId))
                        .ifPresent(reason -> { throw new PayloadValidationException(reason); });
                var snapshot = agentLifecycleService.registerAgent(
                        payload,
                        ConnectionType.WEBSOCKET,
                        null,
                        sessionId,
                        sessionRegistry.getRemoteAddress(sessionId)
                );
                sessionRegistry.markAgentRegistered(sessionId, payload.agentId());
                return toJson(openSocketConnectAccepted(envelope.get("id"), payload, snapshot.sessionId() == null ? sessionId : snapshot.sessionId()));
            }
            if ("event".equals(type) && "agent.heartbeat".equals(event)) {
                Map<String, Object> payloadMap = asMap(envelope.get("payload"));
                AgentHeartbeatPayload payload = objectMapper.convertValue(payloadMap, AgentHeartbeatPayload.class);
                assertRegisteredAgent(sessionId, payload.agentId());
                agentLifecycleService.heartbeat(payload);
                return toJson(openSocketHeartbeatAck(payload));
            }
            if ("event".equals(type) && "agent.capabilities.update".equals(event)) {
                Map<String, Object> payloadMap = asMap(envelope.get("payload"));
                String agentId = textValue(payloadMap.get("agentId"));
                assertRegisteredAgent(sessionId, agentId);
                Map<String, Object> capabilityProfile = asMap(payloadMap.get("capabilityProfile"));
                AgentHeartbeatPayload payload = new AgentHeartbeatPayload(
                        agentId,
                        null,
                        null,
                        OffsetDateTime.now(),
                        null,
                        null,
                        textValue(payloadMap.get("connectionId")),
                        Map.of(),
                        Map.of(),
                        firstNonBlank(textValue(payloadMap.get("capabilityRevision")), textValue(capabilityProfile.get("revision"))),
                        Map.of(),
                        capabilityProfile,
                        Map.of()
                );
                agentLifecycleService.heartbeat(payload);
                return null;
            }
            if ("event".equals(type) && isOpenSocketTaskCallback(event)) {
                Map<String, Object> payloadMap = asMap(envelope.get("payload"));
                String registeredAgentId = sessionRegistry.getAgentId(sessionId);
                String payloadAgentId = firstNonBlank(textValue(payloadMap.get("agentId")), registeredAgentId);
                assertRegisteredAgent(sessionId, payloadAgentId);
                AiEventEnvelope<JsonNode> adapted = toGatewayTaskCallbackEnvelope(envelope, event, payloadMap, registeredAgentId);
                TaskCallbackRelayResult callbackRelayResult = taskCallbackRelay.accept(
                        adapted,
                        ConnectionType.WEBSOCKET,
                        sessionId,
                        registeredAgentId
                );
                if ("task.accepted".equals(event) || "task.progress".equals(event)) {
                    return null;
                }
                return toJson(openSocketTaskCallbackAck(envelope.get("id"), event, payloadMap, callbackRelayResult));
            }
            return toJson(openSocketError(envelope.get("id"), "UNSUPPORTED_OPENSOCKET_EVENT", "Unsupported OpenSocket Agent protocol message: " + type + ":" + (method == null ? event : method), true));
        } catch (AgentAuthorizationDeniedException ex) {
            return toJson(openSocketError(envelope.get("id"), ex.code(), "Agent was not authorized by ai-event-gateway-core", false));
        } catch (PayloadValidationException ex) {
            return toJson(openSocketError(envelope.get("id"), "INVALID_PAYLOAD", ex.getMessage(), false));
        } catch (Exception ex) {
            log.warn("Failed to process OpenSocket envelope. sessionId={}, reason={}", sessionId, ex.getMessage());
            return toJson(openSocketError(envelope.get("id"), "OPENSOCKET_PROTOCOL_ERROR", ex.getMessage(), true));
        }
    }

    private AgentRegisterPayload toAgentRegisterPayload(Map<String, Object> params) {
        String agentId = textValue(params.get("agentId"));
        AgentType agentType = parseAgentType(textValue(params.get("agentType")));
        java.util.LinkedHashSet<String> capabilitySet = new java.util.LinkedHashSet<>(asStringList(params.get("capabilities")));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("opensocketAgentProtocol", true);
        metadata.put("protocolFamily", "OPENSOCKET_AGENT_PROTOCOL");
        copyIfPresent(params, metadata, "protocolVersion");
        copyIfPresent(params, metadata, "pluginName");
        copyIfPresent(params, metadata, "pluginVersion");
        copyIfPresent(params, metadata, "connectionPreferences");
        copyIfPresent(params, metadata, "capabilityProfile");
        Object capabilityProfile = metadata.get("capabilityProfile");
        if (capabilityProfile instanceof Map<?, ?> profile) {
            Object revision = profile.get("revision");
            if (revision != null) metadata.put("capabilityRevision", revision.toString());
            addStrings(capabilitySet, profile.get("supportedTaskTypes"));
            addStrings(capabilitySet, profile.get("supportedIssueProviders"));
            addStrings(capabilitySet, profile.get("toolPolicies"));
            Object executorMode = profile.get("executorMode");
            if (executorMode != null && !executorMode.toString().isBlank()) {
                capabilitySet.add(executorMode.toString().trim());
            }
            Object maxConcurrentTasks = profile.get("maxConcurrentTasks");
            if (maxConcurrentTasks != null) metadata.put("maxConcurrentTasks", maxConcurrentTasks.toString());
            Object placement = profile.get("placement");
            if (placement instanceof Map<?, ?> placementMap) {
                Object region = placementMap.get("region");
                Object zone = placementMap.get("zone");
                Object pool = placementMap.get("pool");
                if (region != null) metadata.put("placementRegion", region.toString());
                if (zone != null) metadata.put("placementZone", zone.toString());
                if (pool != null) metadata.put("placementPool", pool.toString());
            }
        }
        Object auth = params.get("auth");
        if (auth instanceof Map<?, ?> authMap) {
            Object token = authMap.get("token");
            Object fingerprint = authMap.get("publicKeyFingerprint");
            if (token != null && !token.toString().isBlank()) {
                metadata.put("credentialToken", token.toString());
            }
            if (fingerprint != null && !fingerprint.toString().isBlank()) {
                metadata.put("publicKeyFingerprint", fingerprint.toString());
            }
            metadata.put("authType", textValue(authMap.get("type")) == null ? "bearer" : textValue(authMap.get("type")));
        }
        String onboardingToken = textValue(params.get("onboardingToken"));
        return new AgentRegisterPayload(agentId, agentType, ConnectionType.WEBSOCKET, List.copyOf(capabilitySet), metadata, onboardingToken);
    }

    private void addStrings(java.util.LinkedHashSet<String> target, Object value) {
        if (target == null || value == null) {
            return;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    target.add(item.toString().trim());
                }
            }
            return;
        }
        if (!value.toString().isBlank()) {
            target.add(value.toString().trim());
        }
    }

    private Map<String, Object> openSocketConnectAccepted(Object id, AgentRegisterPayload payload, String connectionId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "res");
        if (id != null) response.put("id", id.toString());
        response.put("ok", true);
        response.put("timestamp", OffsetDateTime.now().toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectionId", connectionId);
        body.put("serverTime", OffsetDateTime.now().toString());
        body.put("selectedProtocolVersion", "1.0");
        body.put("heartbeatIntervalMs", 15000);
        body.put("heartbeatAckMode", "optional");
        body.put("heartbeatAckRequired", false);
        body.put("heartbeatAckTimeoutMs", 5000);
        body.put("taskAckMode", "optional");
        body.put("taskAckRequired", false);
        body.put("taskResultAckTimeoutMs", 5000);
        body.put("reconciliationRequired", false);
        body.put("maxPayloadBytes", 1048576);
        body.put("capabilityRevisionAccepted", textValue(payload.metadata().get("capabilityRevision")));
        response.put("payload", body);
        return response;
    }

    private Map<String, Object> openSocketHeartbeatAck(AgentHeartbeatPayload payload) {
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "event");
        ack.put("event", "agent.heartbeat.ack");
        ack.put("timestamp", OffsetDateTime.now().toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("heartbeatId", payload.heartbeatId());
        body.put("connectionId", payload.connectionId());
        body.put("receivedAt", OffsetDateTime.now().toString());
        ack.put("payload", body);
        return ack;
    }

    private Map<String, Object> openSocketError(Object id, String code, String message, boolean retryable) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "res");
        if (id != null) response.put("id", id.toString());
        response.put("ok", false);
        response.put("timestamp", OffsetDateTime.now().toString());
        response.put("error", Map.of("code", code == null ? "ERROR" : code, "message", message == null ? "OpenSocket Agent protocol error" : message, "retryable", retryable));
        return response;
    }


    private boolean isOpenSocketTaskCallback(String event) {
        if (event == null || event.isBlank()) {
            return false;
        }
        return switch (event.trim()) {
            case "task.accepted", "task.progress", "task.completed", "task.failed", "task.rejected", "task.cancelled" -> true;
            default -> false;
        };
    }

    private AiEventEnvelope<JsonNode> toGatewayTaskCallbackEnvelope(
            Map<String, Object> envelope,
            String event,
            Map<String, Object> payloadMap,
            String registeredAgentId
    ) {
        Map<String, Object> payload = normalizeOpenSocketTaskCallbackPayload(event, payloadMap, registeredAgentId, envelope.get("id"));
        MessageType messageType = openSocketTaskCallbackMessageType(event);
        String messageId = firstNonBlank(
                textValue(envelope.get("id")),
                textValue(payload.get("eventId")),
                textValue(payload.get("callbackId")),
                textValue(payload.get("taskId")),
                "opensocket-task-callback-" + java.util.UUID.randomUUID()
        );
        return new AiEventEnvelope<>(
                messageId,
                messageType,
                MessageType.toDomainEventName(messageType),
                firstNonBlank(textValue(payload.get("agentId")), registeredAgentId),
                gatewayProperties.nodeId(),
                OffsetDateTime.now(),
                objectMapper.convertValue(payload, JsonNode.class),
                TraceEnvelope.fromMap(envelope)
        );
    }

    private MessageType openSocketTaskCallbackMessageType(String event) {
        return switch (event) {
            case "task.accepted" -> MessageType.TASK_ACK;
            case "task.progress" -> MessageType.TASK_PROGRESS;
            case "task.completed" -> MessageType.TASK_RESULT;
            case "task.failed", "task.rejected", "task.cancelled" -> MessageType.TASK_ERROR;
            default -> throw new PayloadValidationException("Unsupported OpenSocket task callback event: " + event);
        };
    }

    private Map<String, Object> normalizeOpenSocketTaskCallbackPayload(
            String event,
            Map<String, Object> payloadMap,
            String registeredAgentId,
            Object envelopeId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>(payloadMap == null ? Map.of() : payloadMap);
        if (!payload.containsKey("agentId") || textValue(payload.get("agentId")) == null) {
            payload.put("agentId", registeredAgentId);
        }
        if (!payload.containsKey("callbackId")) {
            String callbackId = firstNonBlank(textValue(payload.get("eventId")), textValue(envelopeId));
            if (callbackId != null) {
                payload.put("callbackId", callbackId);
            }
        }
        if (payload.containsKey("attempt") && !payload.containsKey("attemptNo")) {
            payload.put("attemptNo", payload.get("attempt"));
        }
        payload.putIfAbsent("opensocketEvent", event);
        switch (event) {
            case "task.accepted" -> payload.putIfAbsent("resultStatus", "ACCEPTED");
            case "task.progress" -> payload.putIfAbsent("resultStatus", "RUNNING");
            case "task.completed" -> payload.putIfAbsent("resultStatus", "COMPLETED");
            case "task.failed" -> {
                payload.putIfAbsent("resultStatus", "FAILED");
                Map<String, Object> error = asMap(payload.get("error"));
                payload.putIfAbsent("errorCode", firstNonBlank(textValue(error.get("code")), "TASK_FAILED"));
                payload.putIfAbsent("errorMessage", firstNonBlank(textValue(error.get("message")), "OpenSocket Agent reported task.failed"));
                if (error.containsKey("retryable")) {
                    payload.putIfAbsent("retryable", error.get("retryable"));
                }
            }
            case "task.rejected" -> {
                payload.putIfAbsent("resultStatus", "REJECTED");
                Map<String, Object> rejection = asMap(payload.get("rejection"));
                payload.putIfAbsent("errorCode", firstNonBlank(textValue(rejection.get("reason")), "TASK_REJECTED"));
                payload.putIfAbsent("errorMessage", firstNonBlank(textValue(rejection.get("message")), "OpenSocket Agent rejected task assignment"));
                if (rejection.containsKey("retryable")) {
                    payload.putIfAbsent("retryable", rejection.get("retryable"));
                }
                if (rejection.containsKey("retryAfterMs")) {
                    payload.putIfAbsent("retryAfterMs", rejection.get("retryAfterMs"));
                }
            }
            case "task.cancelled" -> {
                payload.putIfAbsent("resultStatus", "CANCELLED");
                Map<String, Object> cancellation = asMap(payload.get("cancellation"));
                payload.putIfAbsent("errorCode", "TASK_CANCELLED");
                payload.putIfAbsent("errorMessage", firstNonBlank(textValue(cancellation.get("reason")), "OpenSocket Agent cancelled task assignment"));
            }
            default -> throw new PayloadValidationException("Unsupported OpenSocket task callback event: " + event);
        }
        return payload;
    }

    private Map<String, Object> openSocketTaskCallbackAck(Object id, String event, Map<String, Object> payload, TaskCallbackRelayResult relayResult) {
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "event");
        if (id != null) ack.put("id", id.toString() + ":ack");
        ack.put("event", terminalAckEvent(event));
        ack.put("timestamp", OffsetDateTime.now().toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", firstNonBlank(textValue(payload.get("eventId")), textValue(payload.get("callbackId")), textValue(id)));
        body.put("taskId", textValue(payload.get("taskId")));
        body.put("assignmentId", firstNonBlank(textValue(payload.get("assignmentId")), "legacy:" + textValue(payload.get("taskId"))));
        body.put("accepted", relayAccepted(relayResult));
        body.put("serverRevision", gatewayProperties.nodeId());
        body.put("message", relayResult == null ? "Task callback accepted by Netty" : relayResult.message());
        body.put("relayStatus", relayResult == null ? "ACCEPTED" : relayResult.status());
        ack.put("payload", body);
        return ack;
    }

    private String terminalAckEvent(String event) {
        return switch (event) {
            case "task.completed" -> "task.completed.ack";
            case "task.failed" -> "task.failed.ack";
            case "task.rejected" -> "task.rejected.ack";
            case "task.cancelled" -> "task.cancelled.ack";
            case "task.accepted" -> "task.accepted.ack";
            case "task.progress" -> "task.progress.ack";
            default -> "gateway.ack";
        };
    }

    private boolean relayAccepted(TaskCallbackRelayResult relayResult) {
        if (relayResult == null) {
            return true;
        }
        if (relayResult.submitted()) {
            return true;
        }
        return "RELAY_DISABLED".equals(relayResult.status()) || "RELAY_SKIPPED".equals(relayResult.status());
    }

    private Map<String, Object> openSocketGenericAck(Object id, String event, Map<String, Object> payload) {
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "event");
        if (id != null) ack.put("id", id.toString() + ":ack");
        ack.put("event", event);
        ack.put("timestamp", OffsetDateTime.now().toString());
        ack.put("payload", Map.of(
                "agentId", textValue(payload.get("agentId")) == null ? "" : textValue(payload.get("agentId")),
                "accepted", true,
                "receivedAt", OffsetDateTime.now().toString()
        ));
        return ack;
    }

    private AgentType parseAgentType(String value) {
        if (value == null || value.isBlank()) return AgentType.CUSTOM;
        try {
            return AgentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return AgentType.CUSTOM;
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                if (entry.getKey() != null) result.put(entry.getKey().toString(), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(item -> item != null && !item.toString().isBlank()).map(item -> item.toString().trim()).toList();
        }
        return List.of();
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) target.put(key, value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String textValue(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private String gatewayAckStatus(TaskCallbackRelayResult callbackRelayResult, InboundEventRecord inboundForwardRecord) {
        if (callbackRelayResult != null) {
            if (callbackRelayResult.submitted()) {
                return callbackRelayResult.status();
            }
            if ("CALLBACK_CORE_ACCEPTED".equals(callbackRelayResult.status())
                    || "CORE_CALLBACK_ACCEPTED".equals(callbackRelayResult.status())
                    || "CALLBACK_ACCEPTED".equals(callbackRelayResult.status())) {
                return callbackRelayResult.status();
            }
            if ("CALLBACK_CORE_REJECTED".equals(callbackRelayResult.status())
                    || "CORE_CALLBACK_REJECTED".equals(callbackRelayResult.status())) {
                return callbackRelayResult.status();
            }
            if ("QUEUE_FULL".equals(callbackRelayResult.status())) {
                return "REJECTED_BACKPRESSURE";
            }
            if ("RELAY_DISABLED".equals(callbackRelayResult.status()) || "RELAY_SKIPPED".equals(callbackRelayResult.status())) {
                return "ACCEPTED";
            }
            return "RELAY_REJECTED";
        }
        if (inboundForwardRecord != null) {
            if (inboundForwardRecord.status() == InboundForwardStatus.FORWARD_QUEUE_FULL) {
                return "REJECTED_BACKPRESSURE";
            }
            if (inboundForwardRecord.status() == InboundForwardStatus.FORWARD_QUEUED) {
                return "FORWARD_QUEUED";
            }
        }
        return "ACCEPTED";
    }

    private String gatewayAckMessage(TaskCallbackRelayResult callbackRelayResult, InboundEventRecord inboundForwardRecord, String transportName) {
        if (callbackRelayResult != null) {
            return callbackRelayResult.message();
        }
        if (inboundForwardRecord != null) {
            return inboundForwardRecord.message();
        }
        return "Message accepted by ai-event-gateway-netty " + transportName + " transport gateway";
    }

    private void handleTransportSideEffects(String sessionId, WebSocketClientType clientType, AiEventEnvelope<JsonNode> inbound) {
        sessionRegistry.touch(sessionId);

        if (clientType != WebSocketClientType.AGENT) {
            throw new UnsupportedMessageTypeException("WebSocket client type " + clientType + " is receive-only for gateway protocol messages");
        }

        if (inbound.messageType() == MessageType.AGENT_REGISTER) {
            var payload = bindPayload(inbound.payload(), AgentRegisterPayload.class);
            agentOnboardingTokenValidator.validationFailure(payload, sessionRegistry.isAgentOnboardingAuthenticated(sessionId))
                    .ifPresent(reason -> { throw new PayloadValidationException(reason); });
            agentLifecycleService.registerAgent(
                    payload,
                    ConnectionType.WEBSOCKET,
                    null,
                    sessionId,
                    sessionRegistry.getRemoteAddress(sessionId)
            );
            sessionRegistry.markAgentRegistered(sessionId, payload.agentId());
            return;
        }

        if (inbound.messageType() == MessageType.AGENT_HEARTBEAT) {
            var payload = bindPayload(inbound.payload(), AgentHeartbeatPayload.class);
            assertRegisteredAgent(sessionId, payload.agentId());
            agentLifecycleService.heartbeat(payload);
            return;
        }

        if (inbound.messageType() == MessageType.AGENT_STATUS_CHANGE) {
            var payload = bindPayload(inbound.payload(), AgentStatusChangePayload.class);
            assertRegisteredAgent(sessionId, payload.agentId());
            agentLifecycleService.statusChange(payload);
            return;
        }

        if (inbound.messageType() == MessageType.TASK_SUBMIT) {
            guardExternalTaskSubmit();
            return;
        }

        if (inbound.messageType() == MessageType.TASK_DISPATCH) {
            guardExternalTaskDispatch();
            return;
        }

        if (inbound.messageType() == MessageType.TASK_ACK) {
            var payload = bindPayload(inbound.payload(), TaskAckPayload.class);
            assertRegisteredAgent(sessionId, payload.agentId());
            return;
        }

        if (inbound.messageType() == MessageType.TASK_PROGRESS) {
            var payload = bindPayload(inbound.payload(), TaskProgressPayload.class);
            assertRegisteredAgent(sessionId, payload.agentId());
            return;
        }

        if (inbound.messageType() == MessageType.TASK_RESULT) {
            var payload = bindPayload(inbound.payload(), TaskResultPayload.class);
            assertRegisteredAgent(sessionId, payload.agentId());
            return;
        }

        if (inbound.messageType() == MessageType.TASK_ERROR) {
            var payload = bindPayload(inbound.payload(), TaskErrorPayload.class);
            assertRegisteredAgent(sessionId, payload.agentId());
            return;
        }

        throw new UnsupportedMessageTypeException(inbound.messageType().name());
    }

    private void guardExternalTaskSubmit() {
        if (taskAssignmentProperties.disabled()) {
            throw new AssignmentBoundaryViolationException(
                    "TASK_ASSIGNMENT_DISABLED",
                    "Task submission is disabled on this Netty gateway",
                    "gateway.task-assignment.mode=disabled rejects client-originated TASK_SUBMIT"
            );
        }
        throw new AssignmentBoundaryViolationException(
                "TASK_SUBMIT_CORE_ONLY",
                "TASK_SUBMIT must be sent to ai-event-gateway-core",
                "Production Netty runtime does not accept client-originated task submission; Core owns task creation, assignment, dispatch context, retry, recovery, and callback validation"
        );
    }

    private void guardExternalTaskDispatch() {
        if (taskAssignmentProperties.isRejectExternalTaskDispatch()) {
            throw new AssignmentBoundaryViolationException(
                    "TASK_DISPATCH_INTERNAL_DELIVERY_ONLY",
                    "TASK_DISPATCH is only allowed from the internal delivery API",
                    "External clients must not send TASK_DISPATCH directly to TCP/WebSocket; Core dispatches through /internal/delivery/agents/{agentId}/commands"
            );
        }
    }

    private <T> T bindPayload(JsonNode payloadNode, Class<T> payloadType) {
        if (payloadNode == null || payloadNode.isNull()) {
            throw new PayloadValidationException("payload is required");
        }
        final T payload;
        try {
            payload = objectMapper.convertValue(payloadNode, payloadType);
        } catch (IllegalArgumentException ex) {
            throw new PayloadValidationException("payload cannot be converted to " + payloadType.getSimpleName() + ": " + ex.getMessage());
        }
        var violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            var detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new PayloadValidationException(detail);
        }
        return payload;
    }

    private void assertRegisteredAgent(String sessionId, String payloadAgentId) {
        var registeredAgentId = sessionRegistry.getAgentId(sessionId);
        if (registeredAgentId == null || registeredAgentId.isBlank()) {
            throw new PayloadValidationException("WebSocket session must send AGENT_REGISTER before agent-owned messages");
        }
        if (!registeredAgentId.equals(payloadAgentId)) {
            throw new PayloadValidationException("payload agentId does not match registered WebSocket session agentId");
        }
    }

    private AiEventEnvelope<ErrorPayload> error(String code, String message, String detail) {
        return AiEventEnvelope.of(
                MessageType.ERROR,
                gatewayProperties.nodeId(),
                null,
                new ErrorPayload(code, message, detail)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize WebSocket response", ex);
        }
    }

    private static final class AssignmentBoundaryViolationException extends RuntimeException {
        private final String code;
        private final String userMessage;

        private AssignmentBoundaryViolationException(String code, String userMessage, String detail) {
            super(detail);
            this.code = code;
            this.userMessage = userMessage;
        }

        private String code() {
            return code;
        }

        private String userMessage() {
            return userMessage;
        }
    }

    private static final class UnsupportedMessageTypeException extends RuntimeException {
        private UnsupportedMessageTypeException(String messageType) {
            super(messageType);
        }
    }

    private static final class PayloadValidationException extends RuntimeException {
        private PayloadValidationException(String message) {
            super(message);
        }
    }
}
