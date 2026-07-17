package com.opensocket.aievent.core.task;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.decision.DecisionAction;
import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentStatus;

@Service
public class IncidentTaskSuppressionPolicy {
    public TaskSuppressionDecision evaluateResponseTask(Incident incident,
                                                        NormalizedEvent event,
                                                        DedupDecision dedup,
                                                        boolean immediateSeverity,
                                                        boolean reachedThreshold,
                                                        Optional<TaskRecord> existingOpenTask,
                                                        int minOccurrences) {
        if (existingOpenTask != null && existingOpenTask.isPresent()) {
            TaskRecord existing = existingOpenTask.get();
            return TaskSuppressionDecision.suppressed(
                    "Incident already has open response task " + existing.getTaskId()
                            + "; suppressing duplicate task for event " + safeEventId(event),
                    List.of(DecisionAction.SUPPRESS_DUPLICATE_TASK,
                            DecisionAction.SUPPRESS_OPEN_INCIDENT_TASK,
                            DecisionAction.SUPPRESS_NEW_TASK),
                    existing.getTaskId(),
                    TaskType.INCIDENT_RESPONSE);
        }

        if (isOpenIncident(incident) && dedup != null && dedup.duplicate() && !immediateSeverity && !reachedThreshold) {
            long occurrenceCount = dedup.state() == null ? 0L : dedup.state().getOccurrenceCount();
            return TaskSuppressionDecision.suppressed(
                    "Duplicate event aggregated into open incident " + incident.getIncidentId()
                            + "; occurrenceCount=" + occurrenceCount
                            + ", minOccurrences=" + minOccurrences
                            + ", severity=" + (event == null ? null : event.severity()),
                    List.of(DecisionAction.SUPPRESS_DUPLICATE_EVENT,
                            DecisionAction.SUPPRESS_OPEN_INCIDENT_TASK,
                            DecisionAction.SUPPRESS_NEW_TASK),
                    null,
                    TaskType.INCIDENT_RESPONSE);
        }

        if (!immediateSeverity && !reachedThreshold) {
            long occurrenceCount = dedup == null || dedup.state() == null ? 0L : dedup.state().getOccurrenceCount();
            return TaskSuppressionDecision.suppressed(
                    "Incident has not reached task creation threshold: occurrenceCount=" + occurrenceCount
                            + ", minOccurrences=" + minOccurrences
                            + ", severity=" + (event == null ? null : event.severity()),
                    List.of(DecisionAction.SUPPRESS_TASK_BELOW_THRESHOLD,
                            DecisionAction.SUPPRESS_NEW_TASK),
                    null,
                    TaskType.INCIDENT_RESPONSE);
        }

        return TaskSuppressionDecision.proceed();
    }

    public TaskSuppressionDecision evaluateEscalationTask(Incident incident,
                                                          NormalizedEvent event,
                                                          Optional<TaskRecord> existingOpenTask) {
        if (existingOpenTask != null && existingOpenTask.isPresent()) {
            TaskRecord existing = existingOpenTask.get();
            return TaskSuppressionDecision.suppressed(
                    "Incident already has open escalation task " + existing.getTaskId()
                            + "; suppressing duplicate escalation for event " + safeEventId(event),
                    List.of(DecisionAction.SUPPRESS_DUPLICATE_TASK,
                            DecisionAction.SUPPRESS_OPEN_INCIDENT_TASK,
                            DecisionAction.SUPPRESS_NEW_TASK),
                    existing.getTaskId(),
                    TaskType.INCIDENT_ESCALATION);
        }
        return TaskSuppressionDecision.proceed();
    }

    private boolean isOpenIncident(Incident incident) {
        return incident != null
                && (incident.getStatus() == IncidentStatus.ACTIVE || incident.getStatus() == IncidentStatus.ESCALATED);
    }

    private String safeEventId(NormalizedEvent event) {
        return event == null ? "unknown" : event.eventId();
    }
}
