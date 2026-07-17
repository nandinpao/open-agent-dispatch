package com.opensocket.aievent.core.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionFacade;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.agent.AgentControlOperationalQuery;
import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.incident.IncidentOperationalQuery;
import com.opensocket.aievent.core.integration.IntegrationEventOperationalQuery;
import com.opensocket.aievent.core.outbox.OutboxOperationalQuery;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.routing.RoutingDecisionStatus;
import com.opensocket.aievent.core.task.TaskOperationalQuery;

@ExtendWith(MockitoExtension.class)
class OperationalSummaryServiceTest {
    @Mock IncidentOperationalQuery incidents;
    @Mock TaskOperationalQuery tasks;
    @Mock ExecutionOperationalQuery execution;
    @Mock AdapterActionFacade adapterActions;
    @Mock AgentControlOperationalQuery agents;
    @Mock OutboxOperationalQuery outbox;
    @Mock IntegrationEventOperationalQuery integrationEvents;

    @Test
    void composesSummaryOnlyThroughOperationalQueryPorts() {
        when(incidents.incidentStoreMode()).thenReturn("postgresql");
        when(incidents.occurrenceSummaryStoreMode()).thenReturn("postgresql");
        when(tasks.taskStoreMode()).thenReturn("postgresql");
        when(tasks.assignmentStoreMode()).thenReturn("postgresql");
        when(tasks.routingStoreMode()).thenReturn("postgresql");
        when(execution.dispatchStoreMode()).thenReturn("postgresql");
        when(execution.callbackStoreMode()).thenReturn("postgresql");
        when(adapterActions.storeMode()).thenReturn("postgresql");
        when(adapterActions.executorAuditStoreMode()).thenReturn("postgresql");
        when(agents.agentStoreMode()).thenReturn("postgresql");
        when(agents.gatewayStoreMode()).thenReturn("postgresql");
        when(outbox.storeMode()).thenReturn("postgresql");
        when(integrationEvents.storeMode()).thenReturn("postgresql");

        when(incidents.statusCounts(500)).thenReturn(Map.of("ACTIVE", 2));
        when(tasks.taskStatusCounts(500)).thenReturn(Map.of("RUNNING", 1));
        when(execution.dispatchStatusCounts(500)).thenReturn(Map.of("RETRY_WAITING", 1));
        when(adapterActions.statusCounts(500)).thenReturn(Map.of("FAILED", 1));
        when(agents.agentStatusCounts(500)).thenReturn(Map.of("IDLE", 0));
        when(agents.gatewayStatusCounts(500)).thenReturn(Map.of("ONLINE", 1));
        when(outbox.statusCounts(500)).thenReturn(Map.of("DEAD_LETTER", 1));
        when(integrationEvents.statusCounts(500)).thenReturn(Map.of("RETRY_WAITING", 1));
        when(execution.recentCallbacks(500)).thenReturn(List.of(callbackLaggedSeconds(45)));
        when(tasks.recentRoutingDecisions(500)).thenReturn(List.of(routingDecision(RoutingDecisionStatus.NO_CANDIDATE)));
        when(adapterActions.recent(500)).thenReturn(List.of(adapterAction(AdapterActionStatus.FAILED)));

        OperationalSummary summary = new OperationalSummaryService(
                incidents, tasks, execution, adapterActions, agents, outbox, integrationEvents, new ObservabilityProperties()).summary();

        assertThat(summary.getStores()).containsEntry("incident", "postgresql");
        assertThat(summary.getDispatchRequests()).containsEntry("RETRY_WAITING", 1);
        assertThat(summary.getModuleEvents()).containsEntry("DEAD_LETTER", 1);
        assertThat(summary.getIntegrationEvents()).containsEntry("RETRY_WAITING", 1);
        assertThat(summary.getRiskSignals()).containsEntry("requiresAttention", true);
        assertThat(summary.getSloMetrics()).containsKeys("status", "callbackLag", "dispatchReliability", "adapterExecutor", "routingNoCandidate", "alerts");
        assertThat(summary.getSloMetrics()).containsEntry("status", "CRITICAL");
    }

    @Test
    void emitsDisabledSloSnapshotWhenRepositorySummaryDisabled() {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setRepositorySummaryEnabled(false);
        when(incidents.incidentStoreMode()).thenReturn("memory");
        when(incidents.occurrenceSummaryStoreMode()).thenReturn("memory");
        when(tasks.taskStoreMode()).thenReturn("memory");
        when(tasks.assignmentStoreMode()).thenReturn("memory");
        when(tasks.routingStoreMode()).thenReturn("memory");
        when(execution.dispatchStoreMode()).thenReturn("memory");
        when(execution.callbackStoreMode()).thenReturn("memory");
        when(adapterActions.storeMode()).thenReturn("memory");
        when(adapterActions.executorAuditStoreMode()).thenReturn("memory");
        when(agents.agentStoreMode()).thenReturn("memory");
        when(agents.gatewayStoreMode()).thenReturn("memory");
        when(outbox.storeMode()).thenReturn("memory");
        when(integrationEvents.storeMode()).thenReturn("memory");

        OperationalSummary summary = new OperationalSummaryService(
                incidents, tasks, execution, adapterActions, agents, outbox, integrationEvents, properties).summary();

        assertThat(summary.getSloMetrics()).containsEntry("status", "DISABLED");
        assertThat(summary.getSloMetrics()).containsEntry("disabled", true);
    }

    private TaskCallbackRecord callbackLaggedSeconds(long seconds) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskCallbackRecord record = new TaskCallbackRecord();
        record.setCallbackId("cb-" + seconds);
        record.setOccurredAt(now.minusSeconds(seconds));
        record.setProcessedAt(now);
        return record;
    }

    private RoutingDecisionRecord routingDecision(RoutingDecisionStatus status) {
        RoutingDecisionRecord record = new RoutingDecisionRecord();
        record.setDecisionId("route-" + status.name());
        record.setStatus(status);
        record.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return record;
    }

    private AdapterAction adapterAction(AdapterActionStatus status) {
        AdapterAction action = new AdapterAction();
        action.setActionId("act-" + status.name());
        action.setStatus(status);
        return action;
    }
}
