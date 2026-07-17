package com.opensocket.aievent.gateway.netty.protocol;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.opensocket.aievent.gateway.netty.agent.AgentType;
import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentRegisterPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiEventEnvelopeJsonTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void shouldSerializeAndDeserializeAgentRegisterEnvelope() throws Exception {
        var payload = new AgentRegisterPayload(
                "openclaw-agent-001",
                AgentType.OPENCLAW,
                ConnectionType.WEBSOCKET,
                List.of("log-analysis", "code-review"),
                Map.of("host", "agent-host-001")
        );

        var envelope = AiEventEnvelope.of(
                MessageType.AGENT_REGISTER,
                "openclaw-agent-001",
                "gateway-node-001",
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        AiEventEnvelope<AgentRegisterPayload> actual = objectMapper.readValue(
                json,
                new TypeReference<>() {
                }
        );

        assertThat(actual.messageType()).isEqualTo(MessageType.AGENT_REGISTER);
        assertThat(actual.payload().agentId()).isEqualTo("openclaw-agent-001");
        assertThat(actual.payload().agentType()).isEqualTo(AgentType.OPENCLAW);
        assertThat(actual.payload().capabilities()).contains("log-analysis");
    }

    @Test
    void shouldDeserializeProtocolV1DomainEventName() throws Exception {
        String json = """
                {"messageId":"msg-domain-001","eventType":"ai.task.requested","source":"mes","target":"gateway","timestamp":"2026-05-28T10:00:00+08:00","payload":{"taskId":"task-001"}}
                """;

        AiEventEnvelope<Map<String, Object>> actual = objectMapper.readValue(
                json,
                new TypeReference<>() {
                }
        );

        assertThat(actual.messageType()).isEqualTo(MessageType.TASK_SUBMIT);
        assertThat(actual.eventType()).isEqualTo("ai.task.requested");
    }

}
