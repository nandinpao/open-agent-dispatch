package com.opensocket.aievent.core.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.callback.TaskCallbackRequest;
import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentFacade;
import com.opensocket.aievent.core.incident.IncidentObservationCommand;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;

class TaskTerminalAdapterActionPortTest {
    @Test
    void terminalCallbackShouldCreateOnlyOneMcpActionPerTask() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        AdapterActionProperties properties = new AdapterActionProperties();
        properties.getMcp().setEnabled(true);
        properties.getMcp().setOnePerTask(true);
        AdapterActionService service = new AdapterActionService(repository, incidentFacade(), properties);

        TaskRecord task = new TaskRecord();
        task.setTaskId("task-1");
        task.setIncidentId("incident-1");
        task.setStatus(TaskStatus.COMPLETED);
        task.setSiteId("SITE-A");
        task.setPlantId("PLANT-A");
        task.setObjectType("ORDER");
        task.setObjectId("ORDER-1");
        task.setEventType("ERP_ERROR");
        task.setErrorCode("E100");

        DispatchRequest dispatch = new DispatchRequest();
        dispatch.setDispatchRequestId("dispatch-1");
        dispatch.setAssignmentId("assignment-1");
        dispatch.setAgentId("agent-1");

        TaskCallbackRequest callback = new TaskCallbackRequest();
        callback.setCallbackId("callback-1");
        callback.setResultStatus("SUCCESS");

        service.onTerminalTaskCallback(task, dispatch, callback, TaskCallbackType.RESULT);
        service.onTerminalTaskCallback(task, dispatch, callback, TaskCallbackType.RESULT);

        assertThat(repository.findByTaskId("task-1", 10))
                .hasSize(2)
                .allMatch(action -> action.getActionType() == AdapterActionType.MCP_CONTEXT_FETCH)
                .filteredOn(action -> action.getStatus() == AdapterActionStatus.PENDING)
                .hasSize(1);
        assertThat(repository.findByTaskId("task-1", 10))
                .filteredOn(action -> action.getStatus() == AdapterActionStatus.SUPPRESSED)
                .hasSize(1);
    }

    private IncidentFacade incidentFacade() {
        Incident incident = new Incident();
        incident.setIncidentId("incident-1");
        return new IncidentFacade() {
            @Override
            public Incident observe(IncidentObservationCommand command) {
                return incident;
            }

            @Override
            public Incident linkTaskIfAbsent(String incidentId, String taskId) {
                return incident;
            }

            @Override
            public Optional<Incident> findById(String incidentId) {
                return Optional.of(incident);
            }
        };
    }
}
