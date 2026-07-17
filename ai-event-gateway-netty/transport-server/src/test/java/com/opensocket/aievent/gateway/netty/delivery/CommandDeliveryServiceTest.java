package com.opensocket.aievent.gateway.netty.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.gateway.netty.admin.AdminEventMetricsRecorder;
import com.opensocket.aievent.gateway.netty.admin.AdminEventPublisher;
import com.opensocket.aievent.gateway.netty.agent.AgentRegistry;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import com.opensocket.aievent.gateway.netty.tcp.TcpConnectionRegistry;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketSessionRegistry;

import tools.jackson.databind.ObjectMapper;

class CommandDeliveryServiceTest {

    private final GatewayProperties gatewayProperties = new GatewayProperties(
            "gateway-node-test",
            "test",
            "test-version",
            "Test Gateway"
    );
    private final CommandDeliveryService service = new CommandDeliveryService(
            new ObjectMapper(),
            gatewayProperties,
            new AgentRegistry(gatewayProperties),
            new TcpConnectionRegistry(),
            new WebSocketSessionRegistry(),
            AdminEventPublisher.noop(),
            AdminEventMetricsRecorder.noop(),
            new CommandDeliveryTracker(gatewayProperties),
            null
    );

    @Test
    void shouldRejectTaskDispatchWithoutDispatchContextBeforeTransportDelivery() {
        var response = service.deliverToAgent("agent-001", new CommandDeliveryRequest(
                "cmd-001",
                MessageType.TASK_DISPATCH,
                Map.of(
                        "taskId", "task-001",
                        "agentId", "agent-001",
                        "taskType", "demo.command"
                ),
                "trace-001",
                "core-control-plane",
                3000L
        ));

        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.MISSING_DISPATCH_CONTEXT);
        assertThat(response.message()).contains("assignmentId");
        assertThat(response.taskId()).isEqualTo("task-001");
        assertThat(response.attemptNo()).isNull();
        assertThat(service.metrics().missingDispatchContextAttempts()).isEqualTo(1);
    }

    @Test
    void shouldRejectTaskDispatchWhenPayloadAgentDoesNotMatchPathAgent() {
        var response = service.deliverToAgent("agent-001", new CommandDeliveryRequest(
                "cmd-002",
                MessageType.TASK_DISPATCH,
                completeDispatchPayload("agent-002"),
                "trace-002",
                "core-control-plane",
                3000L
        ));

        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.MISSING_DISPATCH_CONTEXT);
        assertThat(response.message()).contains("payload.agentId must match");
    }

    @Test
    void shouldContinueToConnectionResolutionWhenTaskDispatchContextIsComplete() {
        var response = service.deliverToAgent("agent-001", new CommandDeliveryRequest(
                "cmd-003",
                MessageType.TASK_DISPATCH,
                completeDispatchPayload("agent-001"),
                "trace-003",
                "core-control-plane",
                3000L
        ));

        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.AGENT_NOT_CONNECTED);
        assertThat(response.taskId()).isEqualTo("task-001");
        assertThat(response.assignmentId()).isEqualTo("assign-001");
        assertThat(response.dispatchRequestId()).isEqualTo("dispatch-001");
        assertThat(response.attemptNo()).isEqualTo(1);
        assertThat(service.history(10).records()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(service.history(10).records().getFirst().dispatchRequestId()).isEqualTo("dispatch-001");
    }

    @Test
    void shouldAcceptTargetAgentIdAliasForBackwardCompatibleCoreDeliveryPayload() {
        var response = service.deliverToAgent("agent-001", new CommandDeliveryRequest(
                "cmd-004",
                MessageType.TASK_DISPATCH,
                Map.of(
                        "taskId", "task-001",
                        "targetAgentId", "agent-001",
                        "assignmentId", "assign-001",
                        "dispatchRequestId", "dispatch-001",
                        "dispatchToken", "dispatch-token-001",
                        "attemptNo", 1,
                        "taskType", "demo.command"
                ),
                "trace-004",
                "core-control-plane",
                3000L
        ));

        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.AGENT_NOT_CONNECTED);
        assertThat(response.taskId()).isEqualTo("task-001");
        assertThat(response.assignmentId()).isEqualTo("assign-001");
        assertThat(response.dispatchRequestId()).isEqualTo("dispatch-001");
        assertThat(response.attemptNo()).isEqualTo(1);
    }

    private Map<String, Object> completeDispatchPayload(String agentId) {
        return Map.of(
                "taskId", "task-001",
                "agentId", agentId,
                "assignmentId", "assign-001",
                "dispatchRequestId", "dispatch-001",
                "dispatchToken", "dispatch-token-001",
                "attemptNo", 1,
                "taskType", "demo.command"
        );
    }
}
