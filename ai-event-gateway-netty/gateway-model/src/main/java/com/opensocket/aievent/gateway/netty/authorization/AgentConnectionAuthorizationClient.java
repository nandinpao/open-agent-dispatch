package com.opensocket.aievent.gateway.netty.authorization;

public interface AgentConnectionAuthorizationClient {
    AgentConnectionAuthorizationResponse authorize(AgentConnectionAuthorizationRequest request);
}
