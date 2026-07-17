package com.opensocket.aievent.gateway.netty.inbound;

import com.opensocket.aievent.gateway.netty.admin.AdminEventMetricsRecorder;
import com.opensocket.aievent.gateway.netty.admin.AdminEventPublisher;
import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.config.CoreForwardProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundDispatcher;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundRequest;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundStatus;
import org.springframework.beans.factory.annotation.Autowired;
import com.opensocket.aievent.gateway.netty.protocol.AiEventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accepts validated inbound transport messages and optionally forwards them to an external Core
 * endpoint. This service is intentionally transport-only: it does not deduplicate, create tasks,
 * route agents, call MCP, or manage business state.
 *
 * <p>I4 note: Core HTTP I/O is not executed on the caller thread. The caller only submits a bounded
 * outbound work item; dedicated Core outbound workers perform the HTTP call and complete the local
 * inbound tracking record asynchronously.</p>
 */
@Service
public class InboundEventForwarder {

    private static final Logger log = LoggerFactory.getLogger(InboundEventForwarder.class);

    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;
    private final CoreForwardProperties coreForwardProperties;
    private final InboundEventTracker inboundEventTracker;
    private final AdminEventPublisher adminBroadcaster;
    private final AdminEventMetricsRecorder eventMetricsMeter;
    private final CoreOutboundDispatcher coreOutboundDispatcher;

    @Autowired
    public InboundEventForwarder(
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties,
            CoreForwardProperties coreForwardProperties,
            InboundEventTracker inboundEventTracker,
            AdminEventPublisher adminBroadcaster,
            AdminEventMetricsRecorder eventMetricsMeter,
            CoreOutboundDispatcher coreOutboundDispatcher
    ) {
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
        this.coreForwardProperties = coreForwardProperties;
        this.inboundEventTracker = inboundEventTracker;
        this.adminBroadcaster = adminBroadcaster;
        this.eventMetricsMeter = eventMetricsMeter;
        this.coreOutboundDispatcher = coreOutboundDispatcher;
    }

    public InboundEventRecord accept(
            AiEventEnvelope<JsonNode> envelope,
            ConnectionType connectionType,
            String connectionId,
            String agentId
    ) {
        var attempt = inboundEventTracker.begin(envelope, connectionType, connectionId, agentId);
        var shouldRecord = coreForwardProperties.shouldRecord(attempt.category());
        var shouldForward = coreForwardProperties.enabled() && coreForwardProperties.shouldForward(attempt.category());

        if (!shouldForward) {
            var status = coreForwardProperties.enabled()
                    ? InboundForwardStatus.FORWARD_SKIPPED_BY_CATEGORY
                    : InboundForwardStatus.FORWARD_DISABLED;
            var message = coreForwardProperties.enabled()
                    ? "Core forward skipped by inbound event category " + attempt.category()
                    : "Core forwarder disabled; inbound event recorded locally only";
            var record = inboundEventTracker.complete(attempt, status, message, shouldRecord);
            if (shouldRecord) {
                broadcast(record);
            }
            return shouldRecord ? record : null;
        }

        inboundEventTracker.forwardStarted();
        try {
            var outbound = new InboundEventEnvelope(
                    attempt.inboundId(),
                    envelope.messageId(),
                    envelope.messageType(),
                    envelope.eventType(),
                    attempt.category(),
                    envelope.source(),
                    envelope.target(),
                    gatewayProperties.nodeId(),
                    gatewayProperties.siteId(),
                    gatewayProperties.region(),
                    gatewayProperties.zone(),
                    connectionType,
                    connectionId,
                    agentId,
                    envelope.timestamp(),
                    attempt.receivedAt(),
                    envelope.payload()
            );
            var body = objectMapper.writeValueAsString(outbound);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Gateway-Node-Id", gatewayProperties.nodeId());
            headers.put("X-Gateway-Site-Id", gatewayProperties.siteId());
            headers.put("X-Inbound-Event-Category", attempt.category().name());
            if (coreForwardProperties.hasAuthToken()) {
                headers.put("Authorization", "Bearer " + coreForwardProperties.authToken());
            }

            var submission = coreOutboundDispatcher.submit(
                    "inbound event forward",
                    CoreOutboundRequest.jsonPost(URI.create(coreForwardProperties.endpointUrl()), body, headers),
                    result -> completeForward(attempt, result.status(), result.httpStatus(), result.message(), result.responseBody(), shouldRecord)
            );
            if (!submission.accepted()) {
                inboundEventTracker.forwardCompleted();
                eventMetricsMeter.recordFailed();
                var status = submission.status() == CoreOutboundStatus.QUEUE_FULL
                        ? InboundForwardStatus.FORWARD_QUEUE_FULL
                        : InboundForwardStatus.FORWARD_FAILED;
                var record = inboundEventTracker.complete(attempt, status, submission.message(), shouldRecord);
                if (shouldRecord) {
                    broadcast(record);
                }
                return shouldRecord ? record : null;
            }

            var record = inboundEventTracker.complete(
                    attempt,
                    InboundForwardStatus.FORWARD_QUEUED,
                    "Forward queued for Core outbound worker",
                    shouldRecord
            );
            if (shouldRecord) {
                broadcast(record);
            }
            return shouldRecord ? record : null;
        } catch (Exception ex) {
            inboundEventTracker.forwardCompleted();
            eventMetricsMeter.recordFailed();
            log.warn("Failed to queue inbound event forward. messageId={}, messageType={}, category={}, reason={}", envelope.messageId(), envelope.messageType(), attempt.category(), ex.getMessage());
            var record = inboundEventTracker.complete(attempt, InboundForwardStatus.FORWARD_FAILED, ex.getMessage(), shouldRecord);
            if (shouldRecord) {
                broadcast(record);
            }
            return shouldRecord ? record : null;
        }
    }

    public InboundEventHistoryResponse history(int limit) {
        return inboundEventTracker.historyResponse(limit);
    }

    public InboundEventMetrics metrics() {
        return inboundEventTracker.metrics();
    }

    private void completeForward(
            InboundEventTracker.InboundAttempt attempt,
            CoreOutboundStatus status,
            int httpStatus,
            String message,
            String responseBody,
            boolean shouldRecord
    ) {
        try {
            if (status == CoreOutboundStatus.SUCCEEDED && httpStatus >= 200 && httpStatus < 300) {
                eventMetricsMeter.recordRouted();
                var record = inboundEventTracker.complete(
                        attempt,
                        InboundForwardStatus.FORWARDED,
                        "Forwarded to Core endpoint with HTTP " + httpStatus,
                        shouldRecord
                );
                if (shouldRecord) {
                    broadcast(record);
                }
                return;
            }
            eventMetricsMeter.recordFailed();
            var forwardStatus = status == CoreOutboundStatus.TIMEOUT
                    ? InboundForwardStatus.FORWARD_TIMEOUT
                    : InboundForwardStatus.FORWARD_FAILED;
            var detail = httpStatus > 0
                    ? "Core endpoint returned HTTP " + httpStatus + ": " + truncate(responseBody)
                    : (message == null ? status.name() : message);
            var record = inboundEventTracker.complete(attempt, forwardStatus, detail, shouldRecord);
            if (shouldRecord) {
                broadcast(record);
            }
        } finally {
            inboundEventTracker.forwardCompleted();
        }
    }

    private void broadcast(InboundEventRecord record) {
        var data = new LinkedHashMap<String, Object>();
        data.put("inboundId", record.inboundId());
        data.put("messageId", record.messageId() == null ? "" : record.messageId());
        data.put("messageType", record.messageType() == null ? "" : record.messageType().name());
        data.put("eventType", record.eventType() == null ? "" : record.eventType());
        data.put("category", record.category() == null ? "" : record.category().name());
        data.put("source", record.source() == null ? "" : record.source());
        data.put("target", record.target() == null ? "" : record.target());
        data.put("agentId", record.agentId() == null ? "" : record.agentId());
        data.put("connectionType", record.connectionType() == null ? "" : record.connectionType().name());
        data.put("connectionId", record.connectionId() == null ? "" : record.connectionId());
        data.put("status", record.status().name());
        data.put("durationMillis", record.durationMillis());
        adminBroadcaster.broadcast("INBOUND_EVENT_" + record.status().name(), record.message(), data);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }
}
