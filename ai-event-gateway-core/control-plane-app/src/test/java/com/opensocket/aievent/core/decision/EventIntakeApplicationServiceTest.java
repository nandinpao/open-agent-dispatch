package com.opensocket.aievent.core.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import io.micrometer.observation.ObservationRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.intake.IntakeAuthorityView;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.workload.WorkloadContext;

class EventIntakeApplicationServiceTest {
    @AfterEach
    void cleanThreadState() {
        OpenDispatchRequestContextHolder.clear();
        IamTenantContextHolder.clear();
        MDC.clear();
    }

    @Test
    void shouldOwnBusinessObservationContextAndRestoreRequestThreadState() {
        DecisionEngine decisionEngine = mock(DecisionEngine.class);
        EventIntakeDecisionResponse response = response();
        when(decisionEngine.ingest(any())).thenAnswer(invocation -> {
            OpenDispatchRequestContext current = OpenDispatchRequestContextHolder.current().orElseThrow();
            assertThat(current.tenantId()).isEqualTo("tenant-a");
            assertThat(current.correlationId()).isEqualTo("correlation-http");
            assertThat(MDC.get("tenantId")).isEqualTo("tenant-a");
            assertThat(MDC.get("eventStage")).isEqualTo("EXTERNAL");
            assertThat(IamTenantContextHolder.require().tenantId()).isEqualTo("tenant-a");
            return response;
        });

        EventIntakeApplicationService service =
                new EventIntakeApplicationService(decisionEngine, ObservationRegistry.create());
        EventIntakeRequest request = new EventIntakeRequest();
        request.setTenantId("tenant-a");
        request.setSourceSystem("MES");
        request.setEventType("EQUIPMENT_ALARM");

        OpenDispatchRequestContext httpContext = new OpenDispatchRequestContext(
                "request-http", "correlation-http", "", "anonymous", "127.0.0.1", "test", "api");
        MDC.put("traceId", "parent-trace");
        try (OpenDispatchRequestContextHolder.Scope ignored = OpenDispatchRequestContextHolder.open(httpContext)) {
            assertThat(service.intake(request)).isSameAs(response);
            assertThat(OpenDispatchRequestContextHolder.current().orElseThrow()).isEqualTo(httpContext);
        }

        verify(decisionEngine).ingest(request);
        assertThat(MDC.get("traceId")).isEqualTo("parent-trace");
        assertThat(MDC.get("tenantId")).isNull();
        assertThat(MDC.get("eventId")).isNull();
        assertThat(OpenDispatchRequestContextHolder.current()).isEmpty();
    }



    @Test
    void shouldUseServerWorkloadTenantForPersistenceContext() {
        DecisionEngine decisionEngine = mock(DecisionEngine.class);
        EventIntakeDecisionResponse response = response();
        when(decisionEngine.ingest(any())).thenAnswer(invocation -> {
            assertThat(IamTenantContextHolder.require().tenantId()).isEqualTo("tenant-authoritative");
            assertThat(IamTenantContextHolder.require().actorId()).isEqualTo("machine-erp");
            return response;
        });

        EventIntakeApplicationService service =
                new EventIntakeApplicationService(decisionEngine, ObservationRegistry.create());
        EventIntakeRequest request = new EventIntakeRequest();
        request.setTenantId("tenant-body");
        request.setSourceSystem("ERP");
        request.attachServerWorkloadContext(new WorkloadContext(
                "tenant-authoritative", "MACHINE", "machine-erp", "MACHINE", "machine-erp",
                "credential-1", "client-1", "dept-erp", "group-erp", "SOURCE_SYSTEM", "RESOLVED",
                "ERP", "/api/events/intake", "127.0.0.1", "decision-1", 1, 2, 3,
                "request-1", "correlation-1", "trace-1", "JWT", "PRODUCTION", false, java.time.Instant.now()));

        assertThat(service.intake(request)).isSameAs(response);
        assertThat(IamTenantContextHolder.current()).isEmpty();
    }

    @Test
    void shouldRejectUnresolvedPostmanParentTaskTemplateBeforeDecisionEngine() {
        DecisionEngine decisionEngine = mock(DecisionEngine.class);
        EventIntakeApplicationService service =
                new EventIntakeApplicationService(decisionEngine, ObservationRegistry.create());
        EventIntakeRequest request = new EventIntakeRequest();
        request.setTenantId("tenant-a");
        request.setSourceSystem("ERP");
        request.setEventStage("A2A");
        request.setTargetSystem("MES");
        request.setRequestedSkill("MES_WORK_ORDER_TRACE");
        request.setCorrelationId("correlation-001");
        request.setParentTaskId("{{erpTaskId}}");

        assertThatThrownBy(() -> service.intake(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EVENT_INTAKE_TEMPLATE_UNRESOLVED")
                .hasMessageContaining("parentTaskId");
    }

    @Test
    void shouldRequireParentTaskForA2aBeforeDecisionEngine() {
        DecisionEngine decisionEngine = mock(DecisionEngine.class);
        EventIntakeApplicationService service =
                new EventIntakeApplicationService(decisionEngine, ObservationRegistry.create());
        EventIntakeRequest request = new EventIntakeRequest();
        request.setTenantId("tenant-a");
        request.setSourceSystem("ERP");
        request.setEventStage("A2A");
        request.setTargetSystem("MES");
        request.setRequestedSkill("MES_WORK_ORDER_TRACE");
        request.setCorrelationId("correlation-001");

        assertThatThrownBy(() -> service.intake(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A2A_INTAKE_FIELD_REQUIRED")
                .hasMessageContaining("parentTaskId");
    }

    private EventIntakeDecisionResponse response() {
        return new EventIntakeDecisionResponse(
                "event-001",
                "fingerprint-001",
                "incident-001",
                DecisionType.INCIDENT_CREATED,
                false,
                1L,
                "HIGH",
                List.of(DecisionAction.EVENT_NORMALIZED),
                true,
                "task-001",
                "ANALYSIS",
                false,
                "task created",
                true,
                "assignment-001",
                "agent-001",
                "gateway-001",
                "site-001",
                "routing-001",
                "ASSIGNED",
                "eligible",
                true,
                "dispatch-001",
                "QUEUED",
                "AUTO",
                "ELIGIBLE",
                "GATEWAY",
                "queued",
                false,
                false,
                false,
                "decided",
                OffsetDateTime.now(ZoneOffset.UTC),
                "EXTERNAL",
                "MES",
                null,
                null,
                null,
                "correlation-http",
                null,
                "DISPATCH_QUEUED",
                "AGENT_SELECTED",
                "MONITOR_TASK_DELIVERY",
                IntakeAuthorityView.notEvaluated());
    }
}
