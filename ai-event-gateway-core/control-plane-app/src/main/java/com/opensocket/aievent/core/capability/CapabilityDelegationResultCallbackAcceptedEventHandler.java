package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.events.TaskCallbackAcceptedEvent;
import com.opensocket.aievent.core.outbox.ModuleEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Completes Stage 4 by returning an accepted Child result to the current Parent Agent. */
@Component
public class CapabilityDelegationResultCallbackAcceptedEventHandler implements ModuleEventHandler<TaskCallbackAcceptedEvent> {
    private static final Logger log = LoggerFactory.getLogger(CapabilityDelegationResultCallbackAcceptedEventHandler.class);

    private final CapabilityDelegationResultAcceptanceService acceptance;
    private final CapabilityDelegationResultNotifier notifier;
    private final CapabilityDelegationResultNotificationRecorder recorder;

    public CapabilityDelegationResultCallbackAcceptedEventHandler(CapabilityDelegationResultAcceptanceService acceptance,
            CapabilityDelegationResultNotifier notifier, CapabilityDelegationResultNotificationRecorder recorder) {
        this.acceptance = acceptance;
        this.notifier = notifier;
        this.recorder = recorder;
    }

    @Override public String eventType() { return TaskCallbackAcceptedEvent.TYPE; }
    @Override public Class<TaskCallbackAcceptedEvent> payloadType() { return TaskCallbackAcceptedEvent.class; }

    @Override
    public void handle(TaskCallbackAcceptedEvent event) {
        if (event == null || !("RESULT".equalsIgnoreCase(event.callbackType()) || "ERROR".equalsIgnoreCase(event.callbackType()))) return;
        CapabilityDelegationResultNotification notification = acceptance.accept(event);
        if (notification == null || notification.alreadyDelivered()) return;
        CapabilityDelegationResultNotifier.NotificationDeliveryResult delivery = notifier.deliver(notification);
        recorder.record(notification, delivery);
        if (delivery.delivered()) {
            log.info("capability_delegation_result_continuation_delivered delegationId={} parentTaskId={} childTaskId={} parentAgentId={} callbackId={}",
                    notification.delegationId(), notification.parentTaskId(), notification.childTaskId(), notification.parentAgentId(), notification.callbackId());
        } else {
            log.warn("capability_delegation_result_continuation_failed delegationId={} parentTaskId={} childTaskId={} parentAgentId={} status={} message={}",
                    notification.delegationId(), notification.parentTaskId(), notification.childTaskId(), notification.parentAgentId(), delivery.status(), delivery.message());
        }
    }
}
