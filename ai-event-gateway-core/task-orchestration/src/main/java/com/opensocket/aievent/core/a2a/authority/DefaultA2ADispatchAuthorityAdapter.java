package com.opensocket.aievent.core.a2a.authority;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.a2a.A2ARequest;
import com.opensocket.aievent.core.a2a.application.port.out.A2ADispatchAuthorityOperations;
import com.opensocket.aievent.core.events.A2ADispatchRequestedEvent;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;
import com.opensocket.aievent.core.outbox.OutboxEventRecord;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingRepository;

/** Dispatch Authority adapter that persists A2A dispatch intent through the transactional outbox. */
@Component
public class DefaultA2ADispatchAuthorityAdapter implements A2ADispatchAuthorityOperations {
    private final AgentPoolRoutingRepository pools;
    private final ModuleEventPublisher eventPublisher;

    public DefaultA2ADispatchAuthorityAdapter(
            AgentPoolRoutingRepository pools,
            ModuleEventPublisher eventPublisher) {
        this.pools = pools;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void requireActivePool(String tenantId, String poolId) {
        if (pools.findActivePool(tenantId, poolId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Target Agent Pool is missing or inactive: " + poolId);
        }
    }

    @Override
    public DispatchReceipt requestDispatch(DispatchRequest request) {
        requireActivePool(request.tenantId(), request.targetAgentPoolId());
        String eventId = "a2a-dispatch-requested:"
                + request.tenantId() + ":" + request.a2aRequestId();
        OutboxEventRecord outbox = eventPublisher.publish(new A2ADispatchRequestedEvent(
                eventId,
                request.tenantId(),
                request.a2aRequestId(),
                request.childTaskId(),
                request.targetAgentPoolId(),
                request.idempotencyKey(),
                request.correlationId(),
                OffsetDateTime.now(ZoneOffset.UTC)));
        String outboxReference = outbox.getOutboxId() == null
                ? "event:" + eventId
                : "outbox:" + outbox.getOutboxId();
        return new DispatchReceipt(null, 0L, outboxReference, false);
    }

    @Override
    public DispatchReceipt requestDispatch(A2ARequest request) {
        return requestDispatch(new DispatchRequest(
                request.getTenantId(),
                request.getRequestId(),
                request.getChildTaskId(),
                request.getTargetAgentPoolId(),
                "a2a-dispatch:" + request.getRequestId(),
                request.getCorrelationId()));
    }
}
