package com.opensocket.aievent.core.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.a2a.application.port.out.A2ADispatchAuthorityOperations;
import com.opensocket.aievent.core.a2a.application.port.out.A2ATaskAuthorityOperations;
import com.opensocket.aievent.core.a2a.application.service.A2AGovernanceService;

/**
 * Phase 0 architecture gate for the retired directional A2A model.
 *
 * <p>These tests intentionally do not model ERP -> MES, Domain A -> Domain B,
 * target Pool selection, or target Agent selection. New delegation is blocked
 * until the capability-first model is introduced in Phase 1.</p>
 */
class A2AGovernanceServiceTest {
    private A2AGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new A2AGovernanceService(
                mock(A2ATaskAuthorityOperations.class),
                mock(A2ADispatchAuthorityOperations.class),
                mock(A2APolicyRepository.class),
                mock(A2ARequestRepository.class),
                mock(A2AStateHistoryRepository.class),
                mock(A2ARateLimitRepository.class),
                mock(A2AIdempotencyRepository.class),
                mock(A2ADomainEventPublisher.class));
    }

    @Test
    void newDirectionalDelegationIsRetired() {
        assertThatThrownBy(() -> service.request(null))
                .isInstanceOf(A2ARejectedException.class)
                .hasMessageContaining(A2AReasonCode.A2A_LEGACY_ROUTING_RETIRED.name())
                .hasMessageContaining("capability-first")
                .hasMessageContaining("targetDomainId")
                .hasMessageContaining("targetAgentPoolId")
                .hasMessageContaining("targetAgentId");
    }

    @Test
    void legacyDirectionalPolicyWritesAreRetired() {
        assertThatThrownBy(() -> service.upsertPolicy("tenant-test", "legacy-policy", new A2APolicy(), null))
                .isInstanceOf(A2ARejectedException.class)
                .hasMessageContaining(A2AReasonCode.A2A_LEGACY_ROUTING_RETIRED.name())
                .hasMessageContaining("historical evidence");
    }

    @Test
    void legacyApprovalCannotCreateNewChildWorkAfterCutover() {
        assertThatThrownBy(() -> service.approve(
                "tenant-test", "legacy-request", "USER", "operator-test", "phase0-approval"))
                .isInstanceOf(A2ARejectedException.class)
                .hasMessageContaining(A2AReasonCode.A2A_LEGACY_ROUTING_RETIRED.name())
                .hasMessageContaining("cannot create new child work");
    }

    @Test
    void governanceServiceStillDoesNotOwnEvidenceLessCompletionOrCancellationMutation() {
        assertThat(java.util.Arrays.stream(A2AGovernanceService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.equals("complete") || name.equals("cancel"))
                .toList())
                .isEmpty();
    }
}
