package com.opensocket.aievent.core.a2a.application.port.in;

import java.time.OffsetDateTime;
import com.opensocket.aievent.core.a2a.A2ACancellationAckCommand;
import com.opensocket.aievent.core.a2a.A2ACancellationRecord;
import com.opensocket.aievent.core.events.A2ARuntimeCancelDeliveryEvent;

public interface A2ACancellationRuntimeUseCase {
    A2ACancellationRecord recordDelivery(A2ARuntimeCancelDeliveryEvent event);
    A2ACancellationRecord acknowledge(A2ACancellationAckCommand command);
    A2ACancellationRecord reconcile(A2ACancellationRecord due, OffsetDateTime occurredAt);
    void timeout(A2ACancellationRecord due, OffsetDateTime occurredAt);
}
