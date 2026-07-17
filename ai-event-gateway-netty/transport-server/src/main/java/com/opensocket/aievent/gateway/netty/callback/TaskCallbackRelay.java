package com.opensocket.aievent.gateway.netty.callback;

import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.config.CoreTaskCallbackRelayProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.protocol.AiEventEnvelope;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundDispatcher;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundRequest;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Relays Agent task lifecycle callbacks to ai-event-gateway-core control-plane endpoints.
 *
 * <p>The Netty gateway does not validate business lifecycle transitions. It only validates that the
 * callback came from the registered transport connection, enriches gateway/session metadata, and
 * submits the callback to Core. Core remains the source of truth for idempotency, dispatch token
 * validation, attempt fencing, and state transitions.</p>
 */
@Service
public class TaskCallbackRelay {

    private static final Logger log = LoggerFactory.getLogger(TaskCallbackRelay.class);

    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;
    private final CoreTaskCallbackRelayProperties properties;
    private final CoreOutboundDispatcher coreOutboundDispatcher;
    private final TaskCallbackRelayMetrics metrics;

    @Autowired
    public TaskCallbackRelay(
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties,
            CoreTaskCallbackRelayProperties properties,
            CoreOutboundDispatcher coreOutboundDispatcher,
            TaskCallbackRelayMetrics metrics
    ) {
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
        this.properties = properties;
        this.coreOutboundDispatcher = coreOutboundDispatcher;
        this.metrics = metrics == null ? new TaskCallbackRelayMetrics() : metrics;
    }

    public boolean isTaskCallback(MessageType messageType) {
        return messageType == MessageType.TASK_ACK
                || messageType == MessageType.TASK_PROGRESS
                || messageType == MessageType.TASK_RESULT
                || messageType == MessageType.TASK_ERROR;
    }

    public TaskCallbackRelayResult accept(
            AiEventEnvelope<JsonNode> envelope,
            ConnectionType connectionType,
            String connectionId,
            String registeredAgentId
    ) {
        if (envelope == null || !isTaskCallback(envelope.messageType())) {
            return recorded(TaskCallbackRelayResult.skipped(null, "Message is not a task callback"));
        }
        String callbackType = callbackType(envelope.messageType());
        if (!properties.enabled()) {
            log.info("netty_callback_relay_disabled callbackType={} messageId={}", callbackType, envelope.messageId());
            return recorded(TaskCallbackRelayResult.disabled(callbackType));
        }

        Map<String, Object> payload = payloadMap(envelope.payload());
        String taskId = firstNonBlank(stringValue(payload.get("taskId")), envelope.messageId());
        log.info("netty_callback_relay_received taskId={} callbackType={} messageId={} registeredAgentId={} connectionId={} relayEnabled={} requireDispatchContext={}",
                taskId, callbackType, envelope.messageId(), registeredAgentId, connectionId, properties.enabled(), properties.requireDispatchContext());
        if (taskId == null || taskId.isBlank()) {
            log.warn("netty_callback_relay_failed taskId={} callbackType={} reason={}", taskId, callbackType, "TASK_ID_REQUIRED");
            return recorded(TaskCallbackRelayResult.failed(null, callbackType, "taskId is required"));
        }

        Map<String, Object> request = new LinkedHashMap<>();
        copyIfPresent(payload, request, "callbackId");
        copyIfPresent(payload, request, "dispatchRequestId");
        copyIfPresent(payload, request, "assignmentId");
        request.put("taskId", taskId);
        request.put("agentId", firstNonBlank(stringValue(payload.get("agentId")), registeredAgentId, envelope.source()));
        if (properties.enrichGatewayIdentity()) {
            request.put("ownerGatewayNodeId", gatewayProperties.nodeId());
        } else {
            copyIfPresent(payload, request, "ownerGatewayNodeId");
        }
        if (properties.fillMissingAgentSessionId()) {
            request.put("agentSessionId", firstNonBlank(stringValue(payload.get("agentSessionId")), connectionId));
        } else {
            copyIfPresent(payload, request, "agentSessionId");
        }
        copyIfPresent(payload, request, "attemptNo");
        copyIfPresent(payload, request, "dispatchToken");
        copyFirstPresent(payload, request, "fencingToken", "fencingToken", "assignmentFencingToken", "leaseFencingToken");
        copyIfPresent(payload, request, "progressPercent");
        copyIfPresent(payload, request, "message");
        request.put("resultStatus", resultStatus(envelope.messageType(), payload));
        copyIfPresent(payload, request, "errorCode");
        copyIfPresent(payload, request, "errorMessage");
        request.put("occurredAt", firstNonBlank(stringValue(payload.get("occurredAt")), stringValue(payload.get("sentAt")), timestamp(envelope.timestamp())));
        request.put("payload", nestedPayload(envelope.messageType(), payload, connectionType, connectionId));

        String callbackAgentId = stringValue(request.get("agentId"));
        if (!blank(registeredAgentId) && !blank(callbackAgentId) && !registeredAgentId.trim().equals(callbackAgentId.trim())) {
            log.warn("netty_callback_relay_failed taskId={} callbackType={} agentId={} registeredAgentId={} reason={}", taskId, callbackType, callbackAgentId, registeredAgentId, "AGENT_ID_MISMATCH");
            return recorded(TaskCallbackRelayResult.failed(taskId, callbackType, "callback agentId must match the registered Agent session"));
        }

        if (properties.requireDispatchContext()) {
            var missingContextReason = missingDispatchContextReason(request);
            if (missingContextReason != null) {
                log.warn("netty_callback_relay_failed taskId={} callbackType={} dispatchRequestId={} assignmentId={} attemptNo={} reason={}",
                        taskId, callbackType, request.get("dispatchRequestId"), request.get("assignmentId"), request.get("attemptNo"), missingContextReason);
                return recorded(TaskCallbackRelayResult.failed(taskId, callbackType, missingContextReason));
            }
        }

        try {
            var body = objectMapper.writeValueAsString(request);
            String url = callbackUrl(envelope.messageType(), taskId);
            log.info("netty_callback_relay_submit_started taskId={} callbackType={} callbackId={} dispatchRequestId={} assignmentId={} agentId={} attemptNo={} url={}",
                    taskId, callbackType, request.get("callbackId"), request.get("dispatchRequestId"), request.get("assignmentId"), request.get("agentId"), request.get("attemptNo"), url);
            Map<String, String> outboundHeaders = new LinkedHashMap<>();
            outboundHeaders.put("X-Gateway-Node-Id", gatewayProperties.nodeId());
            outboundHeaders.put("X-Gateway-Site-Id", gatewayProperties.siteId());
            outboundHeaders.put("X-Agent-Id", String.valueOf(request.get("agentId")));
            outboundHeaders.put("X-Agent-Session-Id", String.valueOf(request.get("agentSessionId")));
            if (properties.hasAuthToken()) {
                outboundHeaders.put(properties.authHeaderName(), properties.authToken());
            }
            CoreOutboundRequest outboundRequest = CoreOutboundRequest.jsonPost(URI.create(url), body, outboundHeaders);
            if (properties.synchronousTerminalCallbacks() && isTerminalCallback(envelope.messageType())) {
                return recorded(relayTerminalCallbackSynchronously(taskId, callbackType, request, outboundRequest));
            }

            var submission = coreOutboundDispatcher.submit(
                    "task callback relay " + callbackType,
                    outboundRequest,
                    result -> {
                        if (result.success2xx()) {
                            log.info("netty_callback_relay_response taskId={} callbackType={} callbackId={} dispatchRequestId={} agentId={} httpStatus={} status={} success=true",
                                    taskId, callbackType, request.get("callbackId"), request.get("dispatchRequestId"), request.get("agentId"), result.httpStatus(), result.status());
                            return;
                        }
                        if (result.status() == CoreOutboundStatus.HTTP_ERROR) {
                            log.warn("netty_callback_relay_response taskId={} callbackType={} callbackId={} dispatchRequestId={} agentId={} httpStatus={} status={} success=false response={}",
                                    taskId, callbackType, request.get("callbackId"), request.get("dispatchRequestId"), request.get("agentId"), result.httpStatus(), result.status(), truncate(result.responseBody()));
                            return;
                        }
                        log.warn("netty_callback_relay_response taskId={} callbackType={} callbackId={} dispatchRequestId={} agentId={} httpStatus={} status={} success=false reason={}",
                                taskId, callbackType, request.get("callbackId"), request.get("dispatchRequestId"), request.get("agentId"), result.httpStatus(), result.status(), result.message());
                    }
            );
            if (!submission.accepted()) {
                log.warn("netty_callback_relay_rejected taskId={} callbackType={} callbackId={} dispatchRequestId={} status={} reason={}",
                        taskId, callbackType, request.get("callbackId"), request.get("dispatchRequestId"), submission.status(), submission.message());
                return recorded(TaskCallbackRelayResult.rejected(taskId, callbackType, submission.status().name(), submission.message()));
            }
            log.info("netty_callback_relay_submitted taskId={} callbackType={} callbackId={} dispatchRequestId={} assignmentId={} agentId={}",
                    taskId, callbackType, request.get("callbackId"), request.get("dispatchRequestId"), request.get("assignmentId"), request.get("agentId"));
            return recorded(TaskCallbackRelayResult.submitted(taskId, callbackType));
        } catch (Exception ex) {
            log.warn("netty_callback_relay_exception taskId={} callbackType={} reason={}", taskId, callbackType, ex.getMessage());
            return recorded(TaskCallbackRelayResult.failed(taskId, callbackType, ex.getMessage()));
        }
    }


    private TaskCallbackRelayResult relayTerminalCallbackSynchronously(
            String taskId,
            String callbackType,
            Map<String, Object> request,
            CoreOutboundRequest outboundRequest
    ) {
        log.info("netty_callback_relay_sync_started taskId={} callbackType={} callbackId={} dispatchRequestId={} assignmentId={} agentId={} url={}",
                taskId, callbackType, request.get("callbackId"), request.get("dispatchRequestId"), request.get("assignmentId"), request.get("agentId"), outboundRequest.uri());
        try {
            var response = coreOutboundDispatcher.executeSynchronously(
                    "task callback relay " + callbackType, outboundRequest);
            if (response.success2xx()) {
                log.info("netty_callback_relay_sync_response taskId={} callbackType={} callbackId={} dispatchRequestId={} agentId={} httpStatus={} status=CALLBACK_CORE_ACCEPTED success=true",
                        taskId, callbackType, request.get("callbackId"), request.get("dispatchRequestId"), request.get("agentId"), response.httpStatus());
                return TaskCallbackRelayResult.coreAccepted(taskId, callbackType, response.httpStatus());
            }
            log.warn("netty_callback_relay_sync_response taskId={} callbackType={} callbackId={} dispatchRequestId={} agentId={} httpStatus={} status=CALLBACK_CORE_REJECTED success=false response={}",
                    taskId, callbackType, request.get("callbackId"), request.get("dispatchRequestId"), request.get("agentId"), response.httpStatus(), truncate(response.responseBody()));
            return TaskCallbackRelayResult.coreRejected(taskId, callbackType, response.httpStatus(), truncate(response.responseBody()));
        } catch (Exception ex) {
            log.warn("netty_callback_relay_sync_exception taskId={} callbackType={} callbackId={} dispatchRequestId={} reason={}",
                    taskId, callbackType, request.get("callbackId"), request.get("dispatchRequestId"), ex.getMessage());
            return TaskCallbackRelayResult.failed(taskId, callbackType, ex.getMessage());
        }
    }

    private boolean isTerminalCallback(MessageType messageType) {
        return messageType == MessageType.TASK_RESULT || messageType == MessageType.TASK_ERROR;
    }

    private TaskCallbackRelayResult recorded(TaskCallbackRelayResult result) {
        metrics.record(result);
        return result;
    }

    private Map<String, Object> payloadMap(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return new LinkedHashMap<>();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> converted = objectMapper.convertValue(payload, Map.class);
        return converted == null ? new LinkedHashMap<>() : new LinkedHashMap<>(converted);
    }

    private Map<String, Object> nestedPayload(MessageType messageType, Map<String, Object> payload, ConnectionType connectionType, String connectionId) {
        Map<String, Object> nested = new LinkedHashMap<>();
        if (messageType == MessageType.TASK_RESULT && payload.containsKey("result")) {
            nested.put("result", payload.get("result"));
        }
        if (payload.containsKey("externalExecutionKey") && payload.get("externalExecutionKey") != null) {
            nested.put("externalExecutionKey", payload.get("externalExecutionKey"));
        }
        nested.put("agentCallback", new LinkedHashMap<>(payload));
        nested.put("gatewayConnectionType", connectionType == null ? null : connectionType.name());
        nested.put("gatewayConnectionId", connectionId);
        return nested;
    }

    private String missingDispatchContextReason(Map<String, Object> request) {
        if (blank(stringValue(request.get("dispatchRequestId")))) {
            return "dispatchRequestId is required by relay configuration";
        }
        if (properties.requireAssignmentId() && blank(stringValue(request.get("assignmentId")))) {
            return "assignmentId is required by relay configuration";
        }
        if (blank(stringValue(request.get("dispatchToken")))) {
            return "dispatchToken is required by relay configuration";
        }
        if (parsePositiveInteger(request.get("attemptNo")) == null) {
            return "positive attemptNo is required by relay configuration";
        }
        return null;
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

    private String callbackUrl(MessageType messageType, String taskId) {
        return switch (messageType) {
            case TASK_ACK -> properties.callbackUrl(properties.ackPath(), taskId);
            case TASK_PROGRESS -> properties.callbackUrl(properties.progressPath(), taskId);
            case TASK_RESULT -> properties.callbackUrl(properties.resultPath(), taskId);
            case TASK_ERROR -> properties.callbackUrl(properties.errorPath(), taskId);
            default -> throw new IllegalArgumentException("Unsupported task callback type: " + messageType);
        };
    }

    private String callbackType(MessageType messageType) {
        return switch (messageType) {
            case TASK_ACK -> "ACK";
            case TASK_PROGRESS -> "PROGRESS";
            case TASK_RESULT -> "RESULT";
            case TASK_ERROR -> "ERROR";
            default -> null;
        };
    }

    private String resultStatus(MessageType messageType, Map<String, Object> payload) {
        if (payload.containsKey("resultStatus")) {
            return stringValue(payload.get("resultStatus"));
        }
        if (payload.containsKey("status")) {
            return stringValue(payload.get("status"));
        }
        if (messageType == MessageType.TASK_ERROR) {
            return "FAILED";
        }
        if (messageType == MessageType.TASK_RESULT) {
            return "COMPLETED";
        }
        return null;
    }

    private void copyFirstPresent(Map<String, Object> source, Map<String, Object> target, String targetField, String... sourceFields) {
        if (sourceFields == null) {
            return;
        }
        for (String sourceField : sourceFields) {
            if (source.containsKey(sourceField) && source.get(sourceField) != null) {
                target.put(targetField, source.get(sourceField));
                return;
            }
        }
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String field) {
        if (source.containsKey(field) && source.get(field) != null) {
            target.put(field, source.get(field));
        }
    }

    private String timestamp(OffsetDateTime timestamp) {
        return timestamp == null ? null : timestamp.toString();
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value) {
        if (value == null) return "";
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }
}
