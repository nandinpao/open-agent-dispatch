package com.opensocket.aievent.gateway.netty.authorization;

public interface AgentSecurityEventPublisher {
    void publishRejectedConnection(RejectedAgentConnectionSnapshot rejectedConnection);

    void publishDuplicateRuntime(DuplicateRuntimeSecurityEvent duplicateRuntime);

    static AgentSecurityEventPublisher noop() {
        return new AgentSecurityEventPublisher() {
            @Override
            public void publishRejectedConnection(RejectedAgentConnectionSnapshot rejectedConnection) { }

            @Override
            public void publishDuplicateRuntime(DuplicateRuntimeSecurityEvent duplicateRuntime) { }
        };
    }
}
