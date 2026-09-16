package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.security.outbound.OutboundDestinationPolicy;
import com.opensocket.aievent.core.security.outbound.OutboundDestinationValidator;
import com.opensocket.aievent.core.dispatch.DispatchProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Core -> Netty -> current Parent Agent capability-result notification. */
@Component
public class CapabilityDelegationResultNotifier {
    private static final Logger log = LoggerFactory.getLogger(CapabilityDelegationResultNotifier.class);
    private static final String DELIVERY_PATH = "/internal/delivery/agents/{agentId}/commands";

    private final DispatchProperties dispatch;
    private final ObjectMapper json;
    private final HttpClient client;
    private final OutboundDestinationValidator destinations;

    public CapabilityDelegationResultNotifier(DispatchProperties dispatch, ObjectMapper json, OutboundDestinationValidator destinations) {
        this.dispatch = dispatch;
        this.json = json;
        this.destinations = destinations;
        this.client = HttpClient.newBuilder().connectTimeout(dispatch.getClient().getConnectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public NotificationDeliveryResult deliver(CapabilityDelegationResultNotification n) {
        if (n == null) return new NotificationDeliveryResult(false, "NO_NOTIFICATION", "Notification is not required");
        if (n.alreadyDelivered()) return new NotificationDeliveryResult(true, "DELIVERED", "Already delivered");
        if (blank(n.parentAgentId())) return new NotificationDeliveryResult(false, "NO_PARENT_AGENT", "Current Parent Agent is missing");
        try {
            URI uri = destinations.requireAllowed(baseUrl(n.parentGatewayNodeId()) + DELIVERY_PATH.replace("{agentId}", encode(n.parentAgentId())), OutboundDestinationPolicy.registeredEnterpriseService(), "MANAGED_AGENT_CALLBACK");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("delegationId", n.delegationId());
            payload.put("parentTaskId", n.parentTaskId());
            payload.put("childTaskId", n.childTaskId());
            payload.put("capabilityCode", n.capabilityCode());
            payload.put("operation", n.operation());
            payload.put("delegationStatus", n.delegationStatus());
            payload.put("resultStatus", n.resultStatus());
            payload.put("message", n.resultMessage());
            payload.put("errorCode", n.errorCode());
            payload.put("errorMessage", n.errorMessage());
            payload.put("completedByAgentId", n.completedByAgentId());
            payload.put("callbackId", n.callbackId());
            payload.put("payloadHash", n.payloadHash());
            payload.put("parentAssignmentId", n.parentAssignmentId());
            payload.put("parentAgentSessionId", n.parentAgentSessionId());
            payload.put("resultPayload", n.resultPayload());
            payload.put("correlationId", n.correlationId());

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("commandId", n.notificationId());
            envelope.put("messageType", "CAPABILITY_DELEGATION_RESULT");
            envelope.put("payload", payload);
            envelope.put("traceId", first(n.correlationId(), n.delegationId()));
            envelope.put("issuedBy", dispatch.getSourceNodeId());
            envelope.put("timeoutMs", Math.max(100L, dispatch.getClient().getRequestTimeout().toMillis()));

            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(dispatch.getClient().getRequestTimeout())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(envelope)));
            if (!blank(dispatch.getClient().getInternalToken())) {
                builder.header(dispatch.getClient().getInternalTokenHeader(), dispatch.getClient().getInternalToken());
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = parse(response.body());
            String deliveryStatus = body.get("deliveryStatus") == null ? null : String.valueOf(body.get("deliveryStatus"));
            boolean delivered = response.statusCode() >= 200 && response.statusCode() < 300 && "DELIVERED".equalsIgnoreCase(deliveryStatus);
            String message = body.get("message") == null ? response.body() : String.valueOf(body.get("message"));
            log.info("capability_delegation_result_notify delegationId={} notificationId={} parentTaskId={} agentId={} gatewayNode={} httpStatus={} deliveryStatus={}",
                    n.delegationId(), n.notificationId(), n.parentTaskId(), n.parentAgentId(), n.parentGatewayNodeId(), response.statusCode(), deliveryStatus);
            return new NotificationDeliveryResult(delivered, first(deliveryStatus, "HTTP_" + response.statusCode()), message);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new NotificationDeliveryResult(false, "INTERRUPTED", ex.getMessage());
        } catch (Exception ex) {
            log.warn("capability_delegation_result_notify_failed delegationId={} notificationId={} agentId={} reason={}",
                    n.delegationId(), n.notificationId(), n.parentAgentId(), ex.getMessage());
            return new NotificationDeliveryResult(false, "DELIVERY_EXCEPTION", ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String body) {
        if (blank(body)) return Map.of();
        try {
            Map<String, Object> map = json.readValue(body, new TypeReference<Map<String, Object>>() {});
            Object data = map.get("data");
            if (data instanceof Map<?, ?> nested) {
                Map<String, Object> unwrapped = new LinkedHashMap<>();
                nested.forEach((k, v) -> { if (k != null) unwrapped.put(String.valueOf(k), v); });
                return unwrapped;
            }
            return map;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String baseUrl(String gatewayNodeId) {
        String configured = blank(gatewayNodeId) ? null : dispatch.getClient().getGatewayBaseUrls().get(gatewayNodeId);
        String base = blank(configured) ? dispatch.getClient().getDefaultGatewayBaseUrl() : configured;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String first(String a, String b) { return !blank(a) ? a : b; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record NotificationDeliveryResult(boolean delivered, String status, String message) {}
}
