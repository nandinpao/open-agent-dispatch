package com.opensocket.aievent.gateway.netty.inbound;

import com.opensocket.aievent.gateway.netty.config.InboundEventCategory;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;

/** Categorizes validated inbound protocol messages for history and Core forward filtering. */
public final class InboundEventClassifier {

    private InboundEventClassifier() {
    }

    public static InboundEventCategory classify(MessageType messageType) {
        if (messageType == null) {
            return InboundEventCategory.SYSTEM_SIGNAL;
        }
        return switch (messageType) {
            case TASK_RESULT, TASK_ERROR -> InboundEventCategory.BUSINESS_EVENT;
            case TASK_DISPATCH, TASK_ACK, TASK_PROGRESS -> InboundEventCategory.TASK_LIFECYCLE_EVENT;
            case AGENT_HEARTBEAT, CLUSTER_HEARTBEAT -> InboundEventCategory.HEARTBEAT_SIGNAL;
            case AGENT_REGISTER, AGENT_STATUS_CHANGE, CLUSTER_HELLO -> InboundEventCategory.TRANSPORT_SIGNAL;
            case TASK_SUBMIT, ADMIN_EVENT, GATEWAY_ACK, ERROR -> InboundEventCategory.SYSTEM_SIGNAL;
        };
    }
}
