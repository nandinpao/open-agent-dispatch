package com.opensocket.aievent.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AgentA2ARuntimeControllerStage6Test {

    @Test
    void retiredDirectionalRuntimeRouteIsPureGoneTombstone() {
        AgentA2ARuntimeController controller = new AgentA2ARuntimeController();

        assertThatThrownBy(() -> controller.retired("task-parent-001"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException ex = (ResponseStatusException) error;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.GONE);
                    assertThat(ex.getReason()).contains("A2A_LEGACY_RUNTIME_ROUTE_RETIRED");
                    assertThat(ex.getReason()).contains("capability-delegations");
                });
    }
}
