package com.opensocket.aievent.gateway.netty.tcp;

import com.opensocket.aievent.gateway.netty.admin.AdminEventMetricsRecorder;
import com.opensocket.aievent.gateway.netty.agent.AgentLifecycleService;
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

import java.util.stream.Collectors;

/**
 * TCP transport processor. It accepts newline-delimited JSON messages, binds TCP connections to
 * Agents, and acknowledges accepted protocol messages. Business task creation, deduplication,
 * routing, retries, and state updates are intentionally not performed in ai-event-gateway-netty.
 */
@Component
public class TcpMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(TcpMessageProcessor.class);

    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;
    private final TcpConnectionRegistry connectionRegistry;
    private final AgentLifecycleService agentLifecycleService;
    private final AdminEventMetricsRecorder eventMetricsMeter;
    private final AgentOnboardingTokenValidator agentOnboardingTokenValidator;
    private final InboundEventForwarder inboundEventForwarder;
    private final TaskCallbackRelay taskCallbackRelay;
    private final TaskAssignmentProperties taskAssignmentProperties;
    private final AgentProtocolTraceService agentProtocolTraceService;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Autowired
    public TcpMessageProcessor(
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties,
            TcpConnectionRegistry connectionRegistry,
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
        this.connectionRegistry = connectionRegistry;
        this.agentLifecycleService = agentLifecycleService;
        this.eventMetricsMeter = eventMetricsMeter;
        this.agentOnboardingTokenValidator = agentOnboardingTokenValidator;
        this.inboundEventForwarder = inboundEventForwarder;
        this.taskCallbackRelay = taskCallbackRelay;
        this.taskAssignmentProperties = taskAssignmentProperties == null ? new TaskAssignmentProperties() : taskAssignmentProperties;
        this.agentProtocolTraceService = agentProtocolTraceService == null ? AgentProtocolTraceService.noop() : agentProtocolTraceService;
    }

    public TcpMessageProcessor(
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties,
            TcpConnectionRegistry connectionRegistry,
            AgentLifecycleService agentLifecycleService,
            AdminEventMetricsRecorder eventMetricsMeter,
            AgentOnboardingTokenValidator agentOnboardingTokenValidator,
            InboundEventForwarder inboundEventForwarder,
            TaskCallbackRelay taskCallbackRelay,
            TaskAssignmentProperties taskAssignmentProperties
    ) {
        this(objectMapper, gatewayProperties, connectionRegistry, agentLifecycleService, eventMetricsMeter,
                agentOnboardingTokenValidator, inboundEventForwarder, taskCallbackRelay, taskAssignmentProperties,
                AgentProtocolTraceService.noop());
    }

    /**
     * Processes exactly one JSON Line message received from a TCP client. The returned string is also
     * a single-line JSON response that the Netty handler writes back to the same socket.
     */
    public String processLine(String connectionId, String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        try {
            var inbound = objectMapper.readValue(
                    line,
                    new TypeReference<AiEventEnvelope<JsonNode>>() {
                    }
            );
            String agentId = connectionRegistry.getAgentId(connectionId);
            var messageContext = AgentProtocolTraceService.MessageContext.of(
                    "tcp", inbound.messageType(), inbound.eventType(), inbound.messageId(),
                    agentId, connectionId, taskId(inbound.payload()));
            return agentProtocolTraceService.receive(inbound.trace(), messageContext,
                    () -> processInboundEnvelope(connectionId, line, inbound));
        } catch (AssignmentBoundaryViolationException ex) {
            eventMetricsMeter.recordFailed();
            log.warn("Rejected TCP task assignment boundary violation. connectionId={}, reason={}", connectionId, ex.getMessage());
            return toJson(error(ex.code(), ex.userMessage(), ex.getMessage()));
        } catch (UnsupportedMessageTypeException ex) {
            eventMetricsMeter.recordFailed();
            log.warn("Unsupported TCP message type. connectionId={}, reason={}", connectionId, ex.getMessage());
            return toJson(error("UNSUPPORTED_MESSAGE_TYPE", "Unsupported TCP message type", ex.getMessage()));
        } catch (AgentAuthorizationDeniedException ex) {
            eventMetricsMeter.recordFailed();
            log.warn("Agent authorization denied. reason={}", ex.getMessage());
            return toJson(error(ex.code(), "Agent was not authorized by ai-event-gateway-core", ex.getMessage()));
        } catch (PayloadValidationException ex) {
            eventMetricsMeter.recordFailed();
            log.warn("Invalid TCP payload. connectionId={}, reason={}", connectionId, ex.getMessage());
            return toJson(error("INVALID_PAYLOAD", "Protocol payload validation failed", ex.getMessage()));
        } catch (Exception ex) {
            eventMetricsMeter.recordFailed();
            if (ex.getMessage() != null && ex.getMessage().contains("Unsupported messageType/eventType")) {
                log.warn("Unsupported TCP message type. connectionId={}, reason={}", connectionId, ex.getMessage());
                return toJson(error("UNSUPPORTED_MESSAGE_TYPE", "Unsupported TCP message type", ex.getMessage()));
            }
            log.warn("Failed to process TCP message. connectionId={}, reason={}", connectionId, ex.getMessage());
            return toJson(error("INVALID_JSON", "Invalid JSON Line message", ex.getMessage()));
        }
    }

    private String processInboundEnvelope(String connectionId, String line, AiEventEnvelope<JsonNode> inbound) {
        if (inbound.messageType() == null) {
            eventMetricsMeter.recordFailed();
            return toJson(error("INVALID_MESSAGE_TYPE", "messageType is required", line));
        }

        eventMetricsMeter.recordInbound();
        handleTransportSideEffects(connectionId, inbound);
        TaskCallbackRelayResult callbackRelayResult = null;
        InboundEventRecord inboundForwardRecord = null;
        if (taskCallbackRelay.isTaskCallback(inbound.messageType())) {
            log.info("netty_callback_frame_received transport=TCP connectionId={} agentId={} messageType={} messageId={}",
                    connectionId, connectionRegistry.getAgentId(connectionId), inbound.messageType(), inbound.messageId());
            callbackRelayResult = taskCallbackRelay.accept(
                    inbound, ConnectionType.TCP, connectionId, connectionRegistry.getAgentId(connectionId));
            log.info("netty_callback_frame_processed transport=TCP connectionId={} agentId={} messageType={} messageId={} relaySubmitted={} relayStatus={} relayMessage={}",
                    connectionId, connectionRegistry.getAgentId(connectionId), inbound.messageType(), inbound.messageId(),
                    callbackRelayResult == null ? null : callbackRelayResult.submitted(),
                    callbackRelayResult == null ? null : callbackRelayResult.status(),
                    callbackRelayResult == null ? null : callbackRelayResult.message());
        } else {
            inboundForwardRecord = inboundEventForwarder.accept(
                    inbound, ConnectionType.TCP, connectionId, connectionRegistry.getAgentId(connectionId));
        }

        var ack = AiEventEnvelope.of(
                MessageType.GATEWAY_ACK,
                gatewayProperties.nodeId(),
                inbound.source(),
                new GatewayAckPayload(
                        inbound.messageId(), inbound.messageType().name(), connectionId,
                        gatewayAckStatus(callbackRelayResult, inboundForwardRecord),
                        gatewayAckMessage(callbackRelayResult, inboundForwardRecord, "TCP")));

        if (callbackRelayResult != null) {
            log.info("netty_callback_gateway_ack_returned transport=TCP connectionId={} agentId={} messageType={} messageId={} ackStatus={}",
                    connectionId, connectionRegistry.getAgentId(connectionId), inbound.messageType(), inbound.messageId(),
                    ack.payload() == null ? null : ack.payload().status());
        }
        var sendContext = AgentProtocolTraceService.MessageContext.of(
                "tcp", MessageType.GATEWAY_ACK, "gateway.ack", ack.messageId(),
                connectionRegistry.getAgentId(connectionId), connectionId, taskId(inbound.payload()));
        return agentProtocolTraceService.send(sendContext, trace -> toJson(ack.withTrace(trace)));
    }

    private String taskId(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        JsonNode taskId = payload.get("taskId");
        return taskId == null || taskId.isNull() ? null : taskId.asText();
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

    /**
     * Applies transport-only side effects after JSON validation. Agent messages update the local
     * connection view. Client-originated task submission or task dispatch messages are rejected here
     * so production assignment remains owned by ai-event-gateway-core.
     */
    private void handleTransportSideEffects(String connectionId, AiEventEnvelope<JsonNode> inbound) {
        connectionRegistry.touch(connectionId);

        if (inbound.messageType() == MessageType.AGENT_REGISTER) {
            var payload = bindPayload(inbound.payload(), AgentRegisterPayload.class);
            agentOnboardingTokenValidator.validationFailure(payload, false)
                    .ifPresent(reason -> { throw new PayloadValidationException(reason); });
            agentLifecycleService.registerAgent(
                    payload,
                    ConnectionType.TCP,
                    connectionId,
                    null,
                    connectionRegistry.getRemoteAddress(connectionId)
            );
            connectionRegistry.markAgentRegistered(connectionId, payload.agentId());
            return;
        }

        if (inbound.messageType() == MessageType.AGENT_HEARTBEAT) {
            var payload = bindPayload(inbound.payload(), AgentHeartbeatPayload.class);
            assertRegisteredAgent(connectionId, payload.agentId());
            agentLifecycleService.heartbeat(payload);
            return;
        }

        if (inbound.messageType() == MessageType.AGENT_STATUS_CHANGE) {
            var payload = bindPayload(inbound.payload(), AgentStatusChangePayload.class);
            assertRegisteredAgent(connectionId, payload.agentId());
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
            assertRegisteredAgent(connectionId, payload.agentId());
            return;
        }

        if (inbound.messageType() == MessageType.TASK_PROGRESS) {
            var payload = bindPayload(inbound.payload(), TaskProgressPayload.class);
            assertRegisteredAgent(connectionId, payload.agentId());
            return;
        }

        if (inbound.messageType() == MessageType.TASK_RESULT) {
            var payload = bindPayload(inbound.payload(), TaskResultPayload.class);
            assertRegisteredAgent(connectionId, payload.agentId());
            return;
        }

        if (inbound.messageType() == MessageType.TASK_ERROR) {
            var payload = bindPayload(inbound.payload(), TaskErrorPayload.class);
            assertRegisteredAgent(connectionId, payload.agentId());
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

    private void assertRegisteredAgent(String connectionId, String payloadAgentId) {
        var registeredAgentId = connectionRegistry.getAgentId(connectionId);
        if (registeredAgentId == null || registeredAgentId.isBlank()) {
            throw new PayloadValidationException("TCP connection must send AGENT_REGISTER before agent-owned messages");
        }
        if (!registeredAgentId.equals(payloadAgentId)) {
            throw new PayloadValidationException("payload agentId does not match registered TCP connection agentId");
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
            throw new IllegalStateException("Unable to serialize TCP response", ex);
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
