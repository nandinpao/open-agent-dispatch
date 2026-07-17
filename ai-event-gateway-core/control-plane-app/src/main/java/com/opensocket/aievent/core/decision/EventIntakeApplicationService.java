package com.opensocket.aievent.core.decision;

import java.util.LinkedHashMap;
import java.util.Map;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.decision.observation.EventIntakeObservationDocumentation;
import com.opensocket.aievent.core.decision.observation.EventIntakeObservationDocumentation.HighCardinalityKeyNames;
import com.opensocket.aievent.core.decision.observation.EventIntakeObservationDocumentation.LowCardinalityKeyNames;
import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.http.context.MdcContextScope;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;

/** Application boundary for event intake logging, diagnostics and business observation. */
@Service
public class EventIntakeApplicationService {
    private static final Logger log = LoggerFactory.getLogger(EventIntakeApplicationService.class);

    private final DecisionEngine decisionEngine;
    private final ObservationRegistry observationRegistry;

    public EventIntakeApplicationService(DecisionEngine decisionEngine, ObservationRegistry observationRegistry) {
        this.decisionEngine = decisionEngine;
        this.observationRegistry = observationRegistry;
    }

    public EventIntakeDecisionResponse intake(EventIntakeRequest request) {
        String eventStage = firstNonBlank(request.getEventStage(), "EXTERNAL");
        String correlationId = firstNonBlank(
                request.getCorrelationId(),
                OpenDispatchRequestContextHolder.current()
                        .map(OpenDispatchRequestContext::correlationId)
                        .orElse(""));

        Observation observation = EventIntakeObservationDocumentation.EVENT_INTAKE.observation(observationRegistry)
                .lowCardinalityKeyValue(LowCardinalityKeyNames.EVENT_STAGE.withValue(eventStage))
                .lowCardinalityKeyValue(LowCardinalityKeyNames.RESULT.withValue("processing"))
                .highCardinalityKeyValue(HighCardinalityKeyNames.TENANT_ID.withValue(valueOrNone(request.getTenantId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.CORRELATION_ID.withValue(valueOrNone(correlationId)))
                .highCardinalityKeyValue(HighCardinalityKeyNames.SOURCE_SYSTEM.withValue(valueOrNone(request.getSourceSystem())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.EVENT_TYPE.withValue(valueOrNone(request.getEventType())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.REQUESTED_SKILL.withValue(valueOrNone(request.getRequestedSkill())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.PARENT_TASK_ID.withValue(valueOrNone(request.getParentTaskId())));

        Map<String, String> mdc = new LinkedHashMap<>();
        mdc.put("tenantId", request.getTenantId());
        mdc.put("eventStage", eventStage);
        mdc.put("correlationId", correlationId);
        mdc.put("requestedSkill", request.getRequestedSkill());

        try (OpenDispatchRequestContextHolder.Scope ignored =
                     OpenDispatchRequestContextHolder.enrichBusinessContext(request.getTenantId(), correlationId);
             MdcContextScope ignoredMdc = MdcContextScope.open(mdc)) {
            return observation.observe(() -> ingestObserved(request, observation, eventStage, correlationId));
        }
    }

    private EventIntakeDecisionResponse ingestObserved(EventIntakeRequest request,
                                                        Observation observation,
                                                        String eventStage,
                                                        String correlationId) {
        log.info("event_intake_received tenantId={} sourceSystem={} eventStage={} eventType={} originSourceSystem={} targetSystem={} requestedSkill={} handoffMode={} correlationId={} parentTaskId={}",
                safe(request.getTenantId()), safe(request.getSourceSystem()), safe(eventStage), safe(request.getEventType()),
                safe(request.getOriginSourceSystem()), safe(request.getTargetSystem()), safe(request.getRequestedSkill()),
                safe(request.getHandoffMode()), safe(correlationId), safe(request.getParentTaskId()));

        try {
            EventIntakeDecisionResponse response = decisionEngine.ingest(request);
            observation.lowCardinalityKeyValue(LowCardinalityKeyNames.RESULT.withValue("decided"))
                    .lowCardinalityKeyValue(LowCardinalityKeyNames.DECISION_TYPE.withValue(enumName(response.decisionType())))
                    .lowCardinalityKeyValue(LowCardinalityKeyNames.DUPLICATE.withValue(Boolean.toString(response.duplicate())))
                    .lowCardinalityKeyValue(LowCardinalityKeyNames.TASK_CREATED.withValue(Boolean.toString(response.taskCreated())))
                    .lowCardinalityKeyValue(LowCardinalityKeyNames.ASSIGNMENT_CREATED.withValue(Boolean.toString(response.assignmentCreated())))
                    .highCardinalityKeyValue(HighCardinalityKeyNames.EVENT_ID.withValue(valueOrNone(response.eventId())))
                    .highCardinalityKeyValue(HighCardinalityKeyNames.TASK_ID.withValue(valueOrNone(response.taskId())))
                    .highCardinalityKeyValue(HighCardinalityKeyNames.AGENT_ID.withValue(valueOrNone(response.selectedAgentId())))
                    .highCardinalityKeyValue(HighCardinalityKeyNames.ROUTING_DECISION_ID.withValue(valueOrNone(response.routingDecisionId())));

            MdcContextScope.putIfPresent("eventId", response.eventId());
            MdcContextScope.putIfPresent("taskId", response.taskId());
            MdcContextScope.putIfPresent("agentId", response.selectedAgentId());
            log.info("event_intake_decided eventId={} incidentId={} decisionType={} duplicate={} taskCreated={} taskId={} assignmentCreated={} selectedAgentId={} routingDecisionId={} dispatchRequestCreated={} eventStage={} correlationId={} requestedSkill={}",
                    safe(response.eventId()), safe(response.incidentId()), response.decisionType(), response.duplicate(),
                    response.taskCreated(), safe(response.taskId()), response.assignmentCreated(), safe(response.selectedAgentId()),
                    safe(response.routingDecisionId()), response.dispatchRequestCreated(), safe(response.eventStage()),
                    safe(response.correlationId()), safe(response.requestedSkill()));
            return response;
        } catch (RuntimeException ex) {
            observation.lowCardinalityKeyValue(LowCardinalityKeyNames.RESULT.withValue("failed"));
            throw ex;
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? "none" : value.name();
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
