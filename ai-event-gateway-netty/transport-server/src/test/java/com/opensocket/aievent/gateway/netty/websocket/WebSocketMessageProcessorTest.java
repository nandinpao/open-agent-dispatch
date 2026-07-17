package com.opensocket.aievent.gateway.netty.websocket;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.opensocket.aievent.gateway.netty.admin.AdminEventMetricsRecorder;
import com.opensocket.aievent.gateway.netty.admin.AdminEventStore;
import com.opensocket.aievent.gateway.netty.agent.AgentLifecycleService;
import com.opensocket.aievent.gateway.netty.agent.AgentRegistry;
import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.config.AuditLogProperties;
import com.opensocket.aievent.gateway.netty.config.AgentProperties;
import com.opensocket.aievent.gateway.netty.agent.AgentOnboardingTokenValidator;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.config.TaskAssignmentProperties;
import com.opensocket.aievent.gateway.netty.config.CoreForwardProperties;
import com.opensocket.aievent.gateway.netty.config.CoreOutboundProperties;
import com.opensocket.aievent.gateway.netty.config.CoreTaskCallbackRelayProperties;
import com.opensocket.aievent.gateway.netty.callback.TaskCallbackRelay;
import com.opensocket.aievent.gateway.netty.callback.TaskCallbackRelayMetrics;
import com.opensocket.aievent.gateway.netty.inbound.InboundEventForwarder;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundDispatcher;
import com.opensocket.aievent.gateway.netty.inbound.InboundEventTracker;
import com.opensocket.aievent.gateway.netty.admin.audit.NoopAuditEventPersistencePort;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import com.opensocket.aievent.gateway.netty.tcp.TcpConnectionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketMessageProcessorTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final WebSocketSessionRegistry sessionRegistry = new WebSocketSessionRegistry();
    private final GatewayProperties gatewayProperties = new GatewayProperties(
            "gateway-node-test",
            "test",
            "1.2.5-p9-netty-server-test-port-fix-test",
            "test gateway"
    );
    private final AgentRegistry agentRegistry = new AgentRegistry(gatewayProperties);
    private final TcpConnectionRegistry tcpConnectionRegistry = new TcpConnectionRegistry();
    private final AdminEventStore adminEventStore = new AdminEventStore(
            gatewayProperties,
            new AdminProperties(200, List.of("http://localhost:3000")),
            new AuditLogProperties(),
            new NoopAuditEventPersistencePort()
    );
    private final AdminEventMetricsRecorder eventMetricsMeter = AdminEventMetricsRecorder.noop();
    private final WebSocketAdminBroadcaster broadcaster = new WebSocketAdminBroadcaster(objectMapper, gatewayProperties, adminEventStore);
    private final CoreForwardProperties coreForwardProperties = new CoreForwardProperties(
            false,
            "http://localhost:18080",
            "/internal/core/inbound-events",
            3000,
            "",
            500
    );
    private final InboundEventTracker inboundEventTracker = new InboundEventTracker(gatewayProperties, coreForwardProperties);
    private final InboundEventForwarder inboundEventForwarder = new InboundEventForwarder(
            objectMapper,
            gatewayProperties,
            coreForwardProperties,
            inboundEventTracker,
            broadcaster,
            eventMetricsMeter,
            new CoreOutboundDispatcher(new CoreOutboundProperties())
    );
    private final TaskCallbackRelay taskCallbackRelay = new TaskCallbackRelay(
            objectMapper,
            gatewayProperties,
            new CoreTaskCallbackRelayProperties(),
            new CoreOutboundDispatcher(new CoreOutboundProperties()),
            new TaskCallbackRelayMetrics()
    );
    private final AgentLifecycleService lifecycleService = new AgentLifecycleService(
            agentRegistry,
            new AgentProperties(30, 5000),
            broadcaster
    );
    private final WebSocketMessageProcessor processor = new WebSocketMessageProcessor(
            objectMapper,
            gatewayProperties,
            sessionRegistry,
            lifecycleService,
            eventMetricsMeter,
            new AgentOnboardingTokenValidator(new AgentProperties()),
            inboundEventForwarder,
            taskCallbackRelay,
            new TaskAssignmentProperties()
    );

    @Test
    void shouldReturnAckForValidAgentRegisterMessage() {
        var json = """
                {"messageId":"msg-ws-agent-register-001","messageType":"AGENT_REGISTER","source":"openclaw-agent-001","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"agentId":"openclaw-agent-001","agentType":"OPENCLAW","connectionType":"WEBSOCKET","capabilities":["log-analysis"],"metadata":{"host":"local"}}}
                """;

        var response = processor.processText("session-test-001", WebSocketClientType.AGENT, json);

        assertThat(response).contains("\"messageType\":\"" + MessageType.GATEWAY_ACK + "\"");
        assertThat(response).contains("msg-ws-agent-register-001");
        assertThat(response).contains("session-test-001");
        assertThat(agentRegistry.findById("openclaw-agent-001")).isPresent();
        assertThat(adminEventStore.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldReturnErrorForInvalidJson() {
        var response = processor.processText("session-test-001", WebSocketClientType.AGENT, "{invalid json");

        assertThat(response).contains("\"messageType\":\"" + MessageType.ERROR + "\"");
        assertThat(response).contains("INVALID_JSON");
    }

    @Test
    void shouldReturnInvalidPayloadWhenAgentRegisterMissesRequiredFields() {
        var response = processor.processText("session-test-001", WebSocketClientType.AGENT, """
                {"messageId":"msg-ws-invalid-payload-001","messageType":"AGENT_REGISTER","source":"openclaw-agent-001","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"agentId":"openclaw-agent-001"}}
                """);

        assertThat(response).contains("\"messageType\":\"" + MessageType.ERROR + "\"");
        assertThat(response).contains("INVALID_PAYLOAD");
        assertThat(agentRegistry.findById("openclaw-agent-001")).isEmpty();
    }

    @Test
    void shouldRejectAdminWebSocketInboundProtocolMessage() {
        var response = processor.processText("session-admin-unsupported-001", WebSocketClientType.ADMIN, """
                {"messageId":"msg-admin-inbound-001","messageType":"TASK_SUBMIT","source":"admin-ui","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"taskId":"task-admin-001","taskType":"LOG_ANALYSIS"}}
                """);

        assertThat(response).contains("\"messageType\":\"" + MessageType.ERROR + "\"");
        assertThat(response).contains("UNSUPPORTED_MESSAGE_TYPE");
    }

    @Test
    void shouldRejectAgentTaskSubmitInCoreOnlyMode() {
        var response = processor.processText("session-agent-task-submit-001", WebSocketClientType.AGENT, """
                {"messageId":"msg-agent-task-submit-001","messageType":"TASK_SUBMIT","source":"openclaw-agent-001","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"taskId":"task-agent-001","taskType":"LOG_ANALYSIS"}}
                """);

        assertThat(response).contains("\"messageType\":\"" + MessageType.ERROR + "\"");
        assertThat(response).contains("TASK_SUBMIT_CORE_ONLY");
    }

    @Test
    void shouldRejectExternalTaskDispatchInCoreOnlyMode() {
        var response = processor.processText("session-agent-dispatch-001", WebSocketClientType.AGENT, """
                {"messageId":"msg-agent-dispatch-001","messageType":"TASK_DISPATCH","source":"client","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"taskId":"task-agent-001","agentId":"openclaw-agent-001"}}
                """);

        assertThat(response).contains("\"messageType\":\"" + MessageType.ERROR + "\"");
        assertThat(response).contains("TASK_DISPATCH_INTERNAL_DELIVERY_ONLY");
    }

    @Test
    void shouldAcceptOpenSocketAgentConnectAndCapabilityProfile() {
        var response = processor.processText("session-opensocket-connect-001", WebSocketClientType.AGENT, """
                {"type":"req","id":"connect-001","method":"agent.connect","timestamp":"2026-06-18T10:00:00+08:00","params":{"protocolVersion":"1.0","supportedProtocolVersions":["1.0"],"agentId":"openclaw-agent-101","agentType":"OPENCLAW","pluginName":"openclaw-plugin-opensocket","pluginVersion":"1.0.1","capabilities":["TASK_RECEIVE","TASK_PROGRESS","TASK_RESULT"],"capabilityProfile":{"revision":"cap-rev-001","supportedTaskTypes":["ISSUE_ANALYSIS"],"supportedIssueProviders":["JIRA"],"toolPolicies":["READ_ONLY","PROPOSE_ONLY"],"executorMode":"openclaw-runtime","maxConcurrentTasks":2,"placement":{"region":"TW","zone":"TPE","pool":"default"}},"auth":{"type":"bearer","token":"runtime-token"}}}
                """);

        assertThat(response).contains("\"type\":\"res\"");
        assertThat(response).contains("connect-001");
        assertThat(response).contains("heartbeatAckMode");
        var snapshot = agentRegistry.findById("openclaw-agent-101").orElseThrow();
        assertThat(snapshot.metadata()).containsEntry("opensocketAgentProtocol", true);
        assertThat(snapshot.metadata()).doesNotContainKey("credentialToken");
        assertThat(snapshot.capabilities()).contains("ISSUE_ANALYSIS", "JIRA", "READ_ONLY", "PROPOSE_ONLY", "openclaw-runtime");
    }

    @Test
    void shouldAcceptOpenSocketHeartbeatRuntimeLoad() {
        processor.processText("session-opensocket-heartbeat-001", WebSocketClientType.AGENT, """
                {"type":"req","id":"connect-002","method":"agent.connect","params":{"agentId":"openclaw-agent-102","agentType":"OPENCLAW","pluginName":"openclaw-plugin-opensocket","pluginVersion":"1.0.1","capabilities":["TASK_RECEIVE"],"capabilityProfile":{"revision":"cap-rev-002","supportedTaskTypes":["ISSUE_ANALYSIS"],"maxConcurrentTasks":2},"auth":{"type":"bearer","token":"runtime-token"}}}
                """);

        var response = processor.processText("session-opensocket-heartbeat-001", WebSocketClientType.AGENT, """
                {"type":"event","id":"heartbeat-001","event":"agent.heartbeat","timestamp":"2026-06-18T10:00:12+08:00","payload":{"heartbeatId":"heartbeat-001","sequence":1,"agentId":"openclaw-agent-102","connectionId":"session-opensocket-heartbeat-001","status":"BUSY","currentTaskId":"task-001","metrics":{"activeTasks":1,"completedTasks":0,"failedTasks":0,"uptimeSeconds":12},"runtimeLoad":{"activeTasks":1,"maxConcurrentTasks":2,"availableSlots":1,"capacityUtilization":0.5,"outboxPending":0,"outboxInFlight":0,"recoveryPendingAssignments":0,"draining":false},"capabilityRevision":"cap-rev-002","plugin":{"name":"openclaw-plugin-opensocket","version":"1.0.1"}}}
                """);

        assertThat(response).contains("agent.heartbeat.ack");
        var snapshot = agentRegistry.findById("openclaw-agent-102").orElseThrow();
        assertThat(snapshot.metadata()).containsKey("runtimeLoad");
        assertThat(snapshot.metadata()).containsEntry("capabilityRevision", "cap-rev-002");
    }

    @Test
    void shouldMapOpenSocketTaskCompletedToTerminalAck() {
        processor.processText("session-opensocket-task-001", WebSocketClientType.AGENT, """
                {"type":"req","id":"connect-003","method":"agent.connect","params":{"agentId":"openclaw-agent-103","agentType":"OPENCLAW","pluginName":"openclaw-plugin-opensocket","pluginVersion":"1.0.1","capabilities":["TASK_RECEIVE"],"capabilityProfile":{"revision":"cap-rev-003","supportedTaskTypes":["ISSUE_ANALYSIS"],"maxConcurrentTasks":2},"auth":{"type":"bearer","token":"runtime-token"}}}
                """);

        var response = processor.processText("session-opensocket-task-001", WebSocketClientType.AGENT, """
                {"type":"event","id":"task-event-001","event":"task.completed","timestamp":"2026-06-18T10:02:00+08:00","payload":{"eventId":"task-event-001","taskId":"TASK-001","assignmentId":"ASSIGN-001","attempt":1,"agentId":"openclaw-agent-103","connectionId":"session-opensocket-task-001","status":"COMPLETED","result":{"summary":"done","suggestedComment":"ok","suggestedStatus":"IN_PROGRESS","confidence":0.9}}}
                """);

        assertThat(response).contains("task.completed.ack");
        assertThat(response).contains("TASK-001");
        assertThat(response).contains("ASSIGN-001");
    }


}
