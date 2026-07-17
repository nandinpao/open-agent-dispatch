package com.opensocket.aievent.gateway.netty.tcp;

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
import com.opensocket.aievent.gateway.netty.config.InboundEventCategory;
import com.opensocket.aievent.gateway.netty.inbound.InboundEventForwarder;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundDispatcher;
import com.opensocket.aievent.gateway.netty.inbound.InboundEventTracker;
import com.opensocket.aievent.gateway.netty.admin.audit.NoopAuditEventPersistencePort;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketSessionRegistry;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketAdminBroadcaster;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TcpMessageProcessorTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final GatewayProperties gatewayProperties = new GatewayProperties(
            "gateway-node-test",
            "test",
            "1.2.5-p9-netty-server-test-port-fix-test",
            "test gateway"
    );
    private final TcpConnectionRegistry connectionRegistry = new TcpConnectionRegistry();
    private final AgentRegistry agentRegistry = new AgentRegistry(gatewayProperties);
    private final WebSocketSessionRegistry webSocketSessionRegistry = new WebSocketSessionRegistry();
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
    private final AgentLifecycleService agentLifecycleService = new AgentLifecycleService(
            agentRegistry,
            new AgentProperties(30, 5000),
            broadcaster
    );
    private final TcpMessageProcessor processor = new TcpMessageProcessor(
            objectMapper,
            gatewayProperties,
            connectionRegistry,
            agentLifecycleService,
            eventMetricsMeter,
            new AgentOnboardingTokenValidator(new AgentProperties()),
            inboundEventForwarder,
            taskCallbackRelay,
            new TaskAssignmentProperties()
    );

    @Test
    void shouldReturnAckForValidAgentRegisterMessage() {
        var json = """
                {"messageId":"msg-test-001","messageType":"AGENT_REGISTER","source":"openclaw-agent-001","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"agentId":"openclaw-agent-001","agentType":"OPENCLAW","connectionType":"TCP","capabilities":["log-analysis"],"metadata":{"host":"local"}}}
                """;

        var response = processor.processLine("connection-test-001", json);

        assertThat(response).contains("\"messageType\":\"" + MessageType.GATEWAY_ACK + "\"");
        assertThat(response).contains("msg-test-001");
        assertThat(response).contains("connection-test-001");
        assertThat(agentRegistry.findById("openclaw-agent-001")).isPresent();
    }

    @Test
    void shouldReturnAckForAgentHeartbeatMessage() {
        processor.processLine("connection-test-001", """
                {"messageId":"msg-test-001","messageType":"AGENT_REGISTER","source":"openclaw-agent-001","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"agentId":"openclaw-agent-001","agentType":"OPENCLAW","connectionType":"TCP","capabilities":["log-analysis"],"metadata":{"host":"local"}}}
                """);

        var response = processor.processLine("connection-test-001", """
                {"messageId":"msg-test-002","messageType":"AGENT_HEARTBEAT","source":"openclaw-agent-001","target":"gateway-node-test","timestamp":"2026-05-28T10:00:05+08:00","payload":{"agentId":"openclaw-agent-001","status":"BUSY","currentTaskId":"task-001","sentAt":"2026-05-28T10:00:05+08:00"}}
                """);

        assertThat(response).contains("\"messageType\":\"" + MessageType.GATEWAY_ACK + "\"");
        assertThat(agentRegistry.findById("openclaw-agent-001")).isPresent();
        assertThat(agentRegistry.findById("openclaw-agent-001").orElseThrow().currentTaskId()).isEqualTo("task-001");
    }

    @Test
    void shouldReturnErrorForInvalidJson() {
        var response = processor.processLine("connection-test-001", "{invalid json");

        assertThat(response).contains("\"messageType\":\"" + MessageType.ERROR + "\"");
        assertThat(response).contains("INVALID_JSON");
    }

    @Test
    void shouldRejectTaskSubmitBeforePayloadBinding() {
        var response = processor.processLine("connection-test-001", """
                {"messageId":"msg-invalid-payload-001","messageType":"TASK_SUBMIT","source":"mes-demo","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"taskType":"LOG_ANALYSIS"}}
                """);

        assertThat(response).contains("\"messageType\":\"" + MessageType.ERROR + "\"");
        assertThat(response).contains("TASK_SUBMIT_CORE_ONLY");
    }

    @Test
    void shouldRejectProtocolV1TaskRequestedInCoreOnlyMode() {
        var response = processor.processLine("connection-domain-001", """
                {"messageId":"msg-domain-task-001","eventType":"ai.task.requested","source":"mes-demo","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"taskId":"task-domain-001","taskType":"LOG_ANALYSIS","requiredAgentType":"OPENCLAW","requiredCapabilities":["log-analysis"],"input":{"message":"domain event test"}}}
                """);

        assertThat(response).contains("\"messageType\":\"" + MessageType.ERROR + "\"");
        assertThat(response).contains("TASK_SUBMIT_CORE_ONLY");
    }


    @Test
    void shouldReturnUnsupportedMessageTypeInsteadOfAck() {
        var response = processor.processLine("connection-test-unsupported-001", """
                {"messageId":"msg-unsupported-001","messageType":"GATEWAY_ACK","source":"client","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"status":"noop"}}
                """);

        assertThat(response).contains("\"messageType\":\"" + MessageType.ERROR + "\"");
        assertThat(response).contains("UNSUPPORTED_MESSAGE_TYPE");
    }

    @Test
    void shouldRejectAgentMessageWhenConnectionAgentDoesNotMatchPayloadAgent() {
        processor.processLine("connection-test-agent-bound-001", """
                {"messageId":"msg-register-bound-001","messageType":"AGENT_REGISTER","source":"openclaw-agent-001","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"agentId":"openclaw-agent-001","agentType":"OPENCLAW","connectionType":"TCP","capabilities":["log-analysis"],"metadata":{"host":"local"}}}
                """);

        var response = processor.processLine("connection-test-agent-bound-001", """
                {"messageId":"msg-heartbeat-bound-001","messageType":"AGENT_HEARTBEAT","source":"openclaw-agent-002","target":"gateway-node-test","timestamp":"2026-05-28T10:00:05+08:00","payload":{"agentId":"openclaw-agent-002","status":"IDLE","currentTaskId":null,"sentAt":"2026-05-28T10:00:05+08:00"}}
                """);

        assertThat(response).contains("\"messageType\":\"" + MessageType.ERROR + "\"");
        assertThat(response).contains("INVALID_PAYLOAD");
    }

    @Test
    void shouldNotRecordTransportAndHeartbeatSignalsInInboundHistoryByDefault() {
        processor.processLine("connection-filter-001", """
                {"messageId":"msg-filter-register-001","messageType":"AGENT_REGISTER","source":"openclaw-agent-filter-001","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"agentId":"openclaw-agent-filter-001","agentType":"OPENCLAW","connectionType":"TCP","capabilities":["log-analysis"],"metadata":{"host":"local"}}}
                """);

        processor.processLine("connection-filter-001", """
                {"messageId":"msg-filter-heartbeat-001","messageType":"AGENT_HEARTBEAT","source":"openclaw-agent-filter-001","target":"gateway-node-test","timestamp":"2026-05-28T10:00:05+08:00","payload":{"agentId":"openclaw-agent-filter-001","status":"IDLE","currentTaskId":null,"sentAt":"2026-05-28T10:00:05+08:00"}}
                """);

        assertThat(inboundEventTracker.metrics().totalInboundEvents()).isEqualTo(2);
        assertThat(inboundEventTracker.historySize()).isZero();
        assertThat(inboundEventTracker.metrics().byCategory().get(InboundEventCategory.TRANSPORT_SIGNAL)).isEqualTo(1L);
        assertThat(inboundEventTracker.metrics().byCategory().get(InboundEventCategory.HEARTBEAT_SIGNAL)).isEqualTo(1L);
    }

    @Test
    void shouldNotRecordRejectedTaskSubmitInInboundHistoryInCoreOnlyMode() {
        var response = processor.processLine("connection-business-001", """
                {"messageId":"msg-business-task-001","eventType":"ai.task.requested","source":"mes-demo","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"taskId":"task-business-001","taskType":"LOG_ANALYSIS","requiredAgentType":"OPENCLAW","requiredCapabilities":["log-analysis"],"input":{"message":"business event test"}}}
                """);

        assertThat(response).contains("\"messageType\":\"" + MessageType.ERROR + "\"");
        assertThat(response).contains("TASK_SUBMIT_CORE_ONLY");
        assertThat(inboundEventTracker.historySize()).isZero();
    }

    @Test
    void shouldRelayTaskLifecycleAckOutsideGenericInboundHistoryByDefault() {
        processor.processLine("connection-lifecycle-001", """
                {"messageId":"msg-lifecycle-register-001","messageType":"AGENT_REGISTER","source":"openclaw-agent-lifecycle-001","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"agentId":"openclaw-agent-lifecycle-001","agentType":"OPENCLAW","connectionType":"TCP","capabilities":["log-analysis"],"metadata":{"host":"local"}}}
                """);

        var response = processor.processLine("connection-lifecycle-001", """
                {"messageId":"msg-lifecycle-ack-001","messageType":"TASK_ACK","source":"openclaw-agent-lifecycle-001","target":"gateway-node-test","timestamp":"2026-05-28T10:00:10+08:00","payload":{"taskId":"task-lifecycle-001","agentId":"openclaw-agent-lifecycle-001","accepted":true,"message":"accepted"}}
                """);

        assertThat(response).contains("\"messageType\":\"" + MessageType.GATEWAY_ACK + "\"");
        assertThat(inboundEventTracker.historySize()).isZero();
    }

    @Test
    void shouldRejectExternalTaskDispatchInCoreOnlyMode() {
        var response = processor.processLine("connection-dispatch-001", """
                {"messageId":"msg-external-dispatch-001","messageType":"TASK_DISPATCH","source":"client","target":"gateway-node-test","timestamp":"2026-05-28T10:00:00+08:00","payload":{"taskId":"task-001","agentId":"openclaw-agent-001"}}
                """);

        assertThat(response).contains("\"messageType\":\"" + MessageType.ERROR + "\"");
        assertThat(response).contains("TASK_DISPATCH_INTERNAL_DELIVERY_ONLY");
    }


}
