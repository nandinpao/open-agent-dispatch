package com.opensocket.aievent.gateway.netty.directory;

import com.opensocket.aievent.gateway.netty.agent.AgentSnapshot;

import java.util.List;

/**
 * Publishes local Netty Gateway / Agent runtime observations into ai-event-gateway-core's
 * Global Agent Directory.
 *
 * <p>The Netty transport gateway remains the owner of socket connections only. Core remains the
 * owner of assignment, dispatch routing, capacity, and durable task state. This publisher is an
 * anti-corruption boundary between those two responsibilities.</p>
 */
public interface CoreDirectorySyncPublisher {
    void publishGatewayRegistration();
    void publishGatewayHeartbeat();
    void publishAgentConnected(AgentSnapshot agent);
    void publishAgentHeartbeat(AgentSnapshot agent);
    void publishAgentDisconnected(AgentSnapshot agent, String reason);
    void publishGatewaySnapshot(List<AgentSnapshot> agents);

    static CoreDirectorySyncPublisher noop() {
        return new CoreDirectorySyncPublisher() {
            @Override public void publishGatewayRegistration() { }
            @Override public void publishGatewayHeartbeat() { }
            @Override public void publishAgentConnected(AgentSnapshot agent) { }
            @Override public void publishAgentHeartbeat(AgentSnapshot agent) { }
            @Override public void publishAgentDisconnected(AgentSnapshot agent, String reason) { }
            @Override public void publishGatewaySnapshot(List<AgentSnapshot> agents) { }
        };
    }
}
