package com.opensocket.aievent.core.action;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.core.events.TaskTerminalEvent;

class TaskTerminalEventHandlerFinalizationGateTest {

    @Test
    void callbackTerminalEventShouldRemainCompatibilityOnlyAndNotDependOnIssueFinalization() {
        AdapterActionService actions = mock(AdapterActionService.class);
        TaskTerminalEventHandler handler = new TaskTerminalEventHandler(actions);
        TaskTerminalEvent event = event("evt-callback", "RESULT", Map.of());

        handler.handle(event);

        verify(actions).evaluateAfterTaskCallback(any(), any(), any(), eq(TaskCallbackType.RESULT), eq("evt-callback"));
    }

    private TaskTerminalEvent event(String eventId, String callbackType, Map<String,Object> payload) {
        return new TaskTerminalEvent(
                eventId, "task-1", "incident-1", "source-event-1", "COMPLETED", "INCIDENT_RESPONSE", "P2",
                "tenant-a", "TPE", "PLANT-A", "EQUIPMENT", "PUMP-1", "ALARM", null, null, List.of(),
                "dispatch-1", "assignment-1", "agent-1", "gateway-1", "session-1", "callback-1", callbackType,
                "done", "SUCCESS", null, null, payload, OffsetDateTime.now(), "corr-1", "cause-1", "trace-1", "span-1", "AGENT", "agent-1");
    }
}
