package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;

class InMemoryDispatchClaimStateTest {

    @Test
    void persistedDispatchingRequestShouldRestoreOwnershipFence() {
        InMemoryDispatchRequestRepository repository = new InMemoryDispatchRequestRepository();
        OffsetDateTime claimUntil = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);
        DispatchRequest request = new DispatchRequest();
        request.setDispatchRequestId("dispatch-persisted-claim");
        request.setStatus(DispatchRequestStatus.DISPATCHING);
        request.setClaimedBy("dispatch-worker");
        request.setClaimStartedAt(claimUntil.minusMinutes(1));
        request.setClaimUntil(claimUntil);
        request.setCreatedAt(claimUntil.minusMinutes(2));
        request.setUpdatedAt(claimUntil.minusMinutes(1));
        repository.save(request);

        request.setStatus(DispatchRequestStatus.DISPATCHED);
        assertThat(repository.saveClaimed(
                request,
                new ClaimOwnership("dispatch-worker", claimUntil)).applied())
                .isTrue();
    }
}
