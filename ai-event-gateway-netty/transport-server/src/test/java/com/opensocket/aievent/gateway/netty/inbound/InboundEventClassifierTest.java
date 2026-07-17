package com.opensocket.aievent.gateway.netty.inbound;

import com.opensocket.aievent.gateway.netty.config.InboundEventCategory;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InboundEventClassifierTest {

    @Test
    void shouldClassifyBusinessTaskEvents() {
        assertThat(InboundEventClassifier.classify(MessageType.TASK_SUBMIT)).isEqualTo(InboundEventCategory.SYSTEM_SIGNAL);
        assertThat(InboundEventClassifier.classify(MessageType.TASK_RESULT)).isEqualTo(InboundEventCategory.BUSINESS_EVENT);
        assertThat(InboundEventClassifier.classify(MessageType.TASK_ERROR)).isEqualTo(InboundEventCategory.BUSINESS_EVENT);
    }

    @Test
    void shouldClassifyTaskLifecycleEvents() {
        assertThat(InboundEventClassifier.classify(MessageType.TASK_ACK)).isEqualTo(InboundEventCategory.TASK_LIFECYCLE_EVENT);
        assertThat(InboundEventClassifier.classify(MessageType.TASK_PROGRESS)).isEqualTo(InboundEventCategory.TASK_LIFECYCLE_EVENT);
        assertThat(InboundEventClassifier.classify(MessageType.TASK_DISPATCH)).isEqualTo(InboundEventCategory.TASK_LIFECYCLE_EVENT);
    }

    @Test
    void shouldClassifyTransportAndHeartbeatSignals() {
        assertThat(InboundEventClassifier.classify(MessageType.AGENT_REGISTER)).isEqualTo(InboundEventCategory.TRANSPORT_SIGNAL);
        assertThat(InboundEventClassifier.classify(MessageType.AGENT_STATUS_CHANGE)).isEqualTo(InboundEventCategory.TRANSPORT_SIGNAL);
        assertThat(InboundEventClassifier.classify(MessageType.AGENT_HEARTBEAT)).isEqualTo(InboundEventCategory.HEARTBEAT_SIGNAL);
        assertThat(InboundEventClassifier.classify(MessageType.CLUSTER_HEARTBEAT)).isEqualTo(InboundEventCategory.HEARTBEAT_SIGNAL);
    }
}
