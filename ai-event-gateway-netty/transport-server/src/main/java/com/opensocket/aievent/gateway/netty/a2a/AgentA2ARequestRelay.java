package com.opensocket.aievent.gateway.netty.a2a;

import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.config.CoreTaskCallbackRelayProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundDispatcher;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundRequest;
import com.opensocket.aievent.gateway.netty.protocol.AiEventEnvelope;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Relays a capability-first delegation intent from an authenticated Agent session to Core.
 *
 * <p>The Agent credential and enterprise target topology never cross this relay. Source identity
 * is derived exclusively from the Gateway connection registry. Current Core reauthorizes
 * the source Task and resolves a provider-neutral CapabilityRequirement through governance, routing and canonical dispatch.</p>
 */
@Service
public class AgentA2ARequestRelay {
    private static final Logger log = LoggerFactory.getLogger(AgentA2ARequestRelay.class);
    private final ObjectMapper mapper;
    private final GatewayProperties gateway;
    private final CoreTaskCallbackRelayProperties core;
    private final CoreOutboundDispatcher dispatcher;

    public AgentA2ARequestRelay(ObjectMapper mapper, GatewayProperties gateway,
                               CoreTaskCallbackRelayProperties core, CoreOutboundDispatcher dispatcher) {
        this.mapper = mapper; this.gateway = gateway; this.core = core; this.dispatcher = dispatcher;
    }

    public AgentA2ARelayResult accept(AiEventEnvelope<JsonNode> envelope, AgentA2ARequestPayload payload,
                                      ConnectionType transport, String connectionId, String registeredAgentId) {
        String correlationId = text(payload.correlationId(), envelope.messageId());
        log.info("capability_delegation_journey stage=GATEWAY_SESSION_INTENT_RECEIVED correlationId={} taskId={} agentId={} connectionId={} transport={} requestedCapabilityCodes={} credentialExported=false targetTopologyAccepted=false",
                correlationId, payload.taskId(), registeredAgentId, connectionId, transport, payload.requestedCapabilityCodes());
        if (registeredAgentId == null || registeredAgentId.isBlank()) {
            return AgentA2ARelayResult.failed("Authenticated Agent session is required");
        }
        try {
            if (payload.requestedCapabilityCodes() == null || payload.requestedCapabilityCodes().size() != 1
                    || payload.requestedCapabilityCodes().get(0) == null || payload.requestedCapabilityCodes().get(0).isBlank()) {
                return AgentA2ARelayResult.rejected(400, "CAPABILITY_DELEGATION_REQUIRES_EXACTLY_ONE_CAPABILITY");
            }
            String capabilityCode = payload.requestedCapabilityCodes().get(0).trim();
            Map<String,Object> inputContext = new LinkedHashMap<>();
            if (payload.inputPayloadRef() != null && !payload.inputPayloadRef().isBlank()) inputContext.put("inputPayloadRef", payload.inputPayloadRef().trim());
            Map<String,Object> requirement = new LinkedHashMap<>();
            requirement.put("capabilityCode", capabilityCode);
            requirement.put("operation", "EXECUTE");
            requirement.put("inputContext", inputContext);
            requirement.put("resourceConstraints", Map.of());
            requirement.put("dataClassification", payload.sensitivityLevel());
            requirement.put("requiredAssurance", null);
            requirement.put("deadline", null);
            requirement.put("qualityPreference", "BALANCED");
            Map<String,Object> body = new LinkedHashMap<>();
            body.put("requiredCapability", requirement);
            body.put("reason", payload.reason());
            body.put("inputPayloadRef", payload.inputPayloadRef());
            body.put("sensitivityLevel", payload.sensitivityLevel());
            String url = core.baseUrl() + "/internal/control-plane/tasks/" + enc(payload.taskId()) + "/capability-delegations";
            Map<String,String> headers = new LinkedHashMap<>();
            headers.put("X-Gateway-Node-Id", gateway.nodeId());
            headers.put("X-Agent-Id", registeredAgentId);
            headers.put("X-Agent-Session-Id", connectionId == null ? "" : connectionId);
            headers.put("Idempotency-Key", payload.idempotencyKey());
            headers.put("X-Correlation-Id", correlationId);
            if (core.hasAuthToken()) headers.put(core.authHeaderName(), core.authToken());
            log.info("capability_delegation_journey stage=GATEWAY_INTERNAL_RELAY_STARTED correlationId={} taskId={} agentId={} connectionId={} requestedCapabilityCodes={} url={}",
                    correlationId, payload.taskId(), registeredAgentId, connectionId, payload.requestedCapabilityCodes(), url);
            var response = dispatcher.executeSynchronously("agent capability delegation relay",
                    CoreOutboundRequest.jsonPost(URI.create(url), mapper.writeValueAsString(body), headers));
            if (response.success2xx()) {
                AgentA2ARelayResult result = accepted(response.httpStatus(), response.responseBody());
                log.info("capability_delegation_journey stage=GATEWAY_INTERNAL_RELAY_ACCEPTED correlationId={} taskId={} agentId={} httpStatus={} delegationId={} delegationStatus={} childTaskId={} authorizationDecisionId={} routingDecisionId={} adapterResolutionId={} reasonCodes={}",
                        correlationId, payload.taskId(), registeredAgentId, response.httpStatus(), result.delegationId(),
                        result.delegationStatus(), result.childTaskId(), result.authorizationDecisionId(),
                        result.routingDecisionId(), result.adapterResolutionId(), result.reasonCodes());
                return result;
            }
            log.warn("capability_delegation_journey stage=GATEWAY_INTERNAL_RELAY_REJECTED correlationId={} taskId={} agentId={} httpStatus={} response={}",
                    correlationId, payload.taskId(), registeredAgentId, response.httpStatus(), truncate(response.responseBody()));
            return AgentA2ARelayResult.rejected(response.httpStatus(), truncate(response.responseBody()));
        } catch (Exception ex) {
            log.warn("capability_delegation_journey stage=GATEWAY_INTERNAL_RELAY_FAILED correlationId={} taskId={} agentId={} reason={}",
                    correlationId, payload.taskId(), registeredAgentId, ex.getMessage());
            return AgentA2ARelayResult.failed(ex.getMessage());
        }
    }

    private AgentA2ARelayResult accepted(int httpStatus, String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return AgentA2ARelayResult.accepted(httpStatus);
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode receipt = root != null && root.has("data") && root.get("data").isObject() ? root.get("data") : root;
            return AgentA2ARelayResult.accepted(httpStatus, text(receipt, "delegationId"), text(receipt, "status"),
                    text(receipt, "childTaskId"), text(receipt, "authorizationDecisionId"),
                    text(receipt, "routingDecisionId"), text(receipt, "adapterResolutionId"), strings(receipt.get("reasonCodes")));
        } catch (Exception ex) {
            log.warn("capability_delegation_journey stage=GATEWAY_CORE_RECEIPT_PARSE_FAILED httpStatus={} reason={} response={}",
                    httpStatus, ex.getMessage(), truncate(responseBody));
            return AgentA2ARelayResult.accepted(httpStatus);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull()) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText().trim();
    }
    private static java.util.List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) return java.util.List.of();
        java.util.List<String> values = new java.util.ArrayList<>();
        node.forEach(value -> { if (value != null && !value.isNull() && !value.asText().isBlank()) values.add(value.asText().trim()); });
        return java.util.List.copyOf(values);
    }
    private static String enc(String v){return java.net.URLEncoder.encode(v==null?"":v, StandardCharsets.UTF_8);}
    private static String text(String a,String b){return a!=null&&!a.isBlank()?a.trim():(b==null?"":b.trim());}
    private static String truncate(String v){if(v==null)return ""; return v.length()>800?v.substring(0,800):v;}
}
