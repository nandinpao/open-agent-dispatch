package com.opensocket.aievent.core.decision;

import java.util.LinkedHashMap;
import java.util.Map;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.opensocket.aievent.core.decision.observation.EventIntakeObservationDocumentation;
import com.opensocket.aievent.core.decision.observation.EventIntakeObservationDocumentation.HighCardinalityKeyNames;
import com.opensocket.aievent.core.decision.observation.EventIntakeObservationDocumentation.LowCardinalityKeyNames;
import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.intake.IntakeAdmission;
import com.opensocket.aievent.core.intake.IntakeAuthorityService;
import com.opensocket.aievent.core.http.context.MdcContextScope;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.workload.WorkloadContext;

/** Application boundary for event intake logging, diagnostics and business observation. */
@Service
public class EventIntakeApplicationService {
    private static final Logger log = LoggerFactory.getLogger(EventIntakeApplicationService.class);

    private final DecisionEngine decisionEngine;
    private final ObservationRegistry observationRegistry;
    private final IntakeAuthorityService intakeAuthorityService;
    private final TransactionTemplate materializationTransaction;

    @org.springframework.beans.factory.annotation.Autowired
    public EventIntakeApplicationService(DecisionEngine decisionEngine, ObservationRegistry observationRegistry,
                                         ObjectProvider<IntakeAuthorityService> intakeAuthorityService,
                                         ObjectProvider<PlatformTransactionManager> transactionManager) {
        this.decisionEngine = decisionEngine;
        this.observationRegistry = observationRegistry;
        this.intakeAuthorityService = intakeAuthorityService == null ? null : intakeAuthorityService.getIfAvailable();
        PlatformTransactionManager manager = transactionManager == null ? null : transactionManager.getIfAvailable();
        this.materializationTransaction = manager == null ? null : new TransactionTemplate(manager);
    }

    /** Test/standalone compatibility constructor. Production Spring wiring uses the A0-R1 authority constructor above. */
    public EventIntakeApplicationService(DecisionEngine decisionEngine, ObservationRegistry observationRegistry) {
        this.decisionEngine = decisionEngine;
        this.observationRegistry = observationRegistry;
        this.intakeAuthorityService = null;
        this.materializationTransaction = null;
    }

    public EventIntakeDecisionResponse intake(EventIntakeRequest request) {
        String eventStage = firstNonBlank(request.getEventStage(), "EXTERNAL");
        validateRequestPlaceholders(request, eventStage);
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

        WorkloadContext workload = request.serverWorkloadContext();
        String persistenceTenant = workload == null
                ? firstNonBlank(request.getTenantId(), "INSTANCE")
                : firstNonBlank(workload.tenantId(), request.getTenantId(), "INSTANCE");
        String persistenceActor = workload == null
                ? "core-internal-event_ingestion"
                : firstNonBlank(workload.actorPrincipalId(), workload.originPrincipalId(), "core-internal-event_ingestion");

        Map<String, String> mdc = new LinkedHashMap<>();
        mdc.put("tenantId", persistenceTenant);
        mdc.put("eventStage", eventStage);
        mdc.put("correlationId", correlationId);
        mdc.put("requestedSkill", request.getRequestedSkill());

        log.info("event_intake_persistence_context tenantId={} actorId={} workloadContextPresent={} correlationId={}",
                safe(persistenceTenant), safe(persistenceActor), workload != null, safe(correlationId));

        try (OpenDispatchRequestContextHolder.Scope ignored =
                     OpenDispatchRequestContextHolder.enrichBusinessContext(persistenceTenant, correlationId);
             MdcContextScope ignoredMdc = MdcContextScope.open(mdc);
             IamTenantContextHolder.Scope ignoredTenant = IamTenantContextHolder.open(
                     new IamTenantExecutionContext(persistenceTenant, persistenceActor))) {
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

        IntakeAdmission intakeAdmission = intakeAuthorityService == null ? null : intakeAuthorityService.admit(request);
        try {
            EventIntakeDecisionResponse response;
            if (intakeAdmission != null && !intakeAdmission.proceedToLegacyMaterialization()) {
                response = intakeAdmission.replayResponse();
            } else if (intakeAdmission != null && materializationTransaction != null) {
                response = materializationTransaction.execute(status -> {
                    EventIntakeDecisionResponse decided = decisionEngine.ingest(request);
                    return intakeAuthorityService.complete(intakeAdmission, decided);
                });
                if (response == null) throw new IllegalStateException("A0R1_MATERIALIZATION_TRANSACTION_RETURNED_NULL");
            } else {
                response = decisionEngine.ingest(request);
                if (intakeAdmission != null) response = intakeAuthorityService.complete(intakeAdmission, response);
            }
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
            if (intakeAuthorityService != null && intakeAdmission != null && intakeAdmission.proceedToLegacyMaterialization()) {
                try { intakeAuthorityService.deferAfterMaterializationFailure(intakeAdmission, ex); }
                catch (RuntimeException deferFailure) {
                    log.error("intake_defer_after_materialization_failure_failed ingestionId={} error={}",
                            safe(intakeAdmission.ingestionId()), safe(deferFailure.getMessage()), deferFailure);
                }
            }
            throw ex;
        }
    }


    private void validateRequestPlaceholders(EventIntakeRequest request, String eventStage) {
        rejectUnresolvedTemplate("tenantId", request.getTenantId());
        rejectUnresolvedTemplate("sourceSystem", request.getSourceSystem());
        rejectUnresolvedTemplate("correlationId", request.getCorrelationId());
        rejectUnresolvedTemplate("parentTaskId", request.getParentTaskId());
        rejectUnresolvedTemplate("targetSystem", request.getTargetSystem());
        rejectUnresolvedTemplate("requestedSkill", request.getRequestedSkill());

        if ("A2A".equalsIgnoreCase(eventStage)) {
            requireA2aField("correlationId", request.getCorrelationId());
            requireA2aField("parentTaskId", request.getParentTaskId());
            requireA2aField("targetSystem", request.getTargetSystem());
            requireA2aField("requestedSkill", request.getRequestedSkill());
        }
    }

    private void rejectUnresolvedTemplate(String field, String value) {
        if (value == null) return;
        String normalized = value.trim();
        if (normalized.contains("{{") || normalized.contains("}}")) {
            throw new IllegalArgumentException(
                    "EVENT_INTAKE_TEMPLATE_UNRESOLVED: " + field
                            + " still contains an unresolved client template variable: " + normalized);
        }
    }

    private void requireA2aField(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "A2A_INTAKE_FIELD_REQUIRED: " + field + " is required when eventStage=A2A");
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? "none" : value.name();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
