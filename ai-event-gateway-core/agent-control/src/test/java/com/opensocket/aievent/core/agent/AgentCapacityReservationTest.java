package com.opensocket.aievent.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

class AgentCapacityReservationTest {
    @Test
    void onlyOneConcurrentReservationShouldWinWhenAgentCapacityIsOne() throws Exception {
        AgentDirectoryService service = new AgentDirectoryService(new InMemoryAgentDirectoryRepository());
        AgentSnapshot agent = new AgentSnapshot();
        agent.setAgentId("agent-1");
        agent.setAgentType("OPENCLAW");
        agent.setOwnerGatewayNodeId("gateway-1");
        agent.setStatus(AgentStatus.IDLE);
        agent.setCapabilities(List.of("issue-analysis"));
        agent.setMaxConcurrentTasks(1);
        service.register(agent);

        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<CapacityReservationResult>> attempts = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<CapacityReservationResult>) () -> service.reserveCapacity("agent-1"))
                    .toList();
            long winners = executor.invokeAll(attempts).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .filter(CapacityReservationResult::reserved)
                    .count();

            assertThat(winners).isEqualTo(1);
        }

        AgentSnapshot reserved = service.findById("agent-1").orElseThrow();
        assertThat(reserved.getReservedTaskCount()).isEqualTo(1);
        assertThat(reserved.getEffectiveTaskCount()).isEqualTo(1);
        assertThat(reserved.isAssignable()).isFalse();

        assertThat(service.releaseCapacity("agent-1")).isTrue();
        AgentSnapshot released = service.findById("agent-1").orElseThrow();
        assertThat(released.getReservedTaskCount()).isZero();
        assertThat(released.getStatus()).isEqualTo(AgentStatus.IDLE);
        assertThat(released.isAssignable()).isTrue();
    }
}
