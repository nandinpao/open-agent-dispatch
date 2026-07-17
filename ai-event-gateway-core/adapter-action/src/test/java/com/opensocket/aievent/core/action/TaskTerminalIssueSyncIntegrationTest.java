package com.opensocket.aievent.core.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionService;
import com.opensocket.aievent.core.action.executor.AdapterExecutorCircuitBreaker;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditService;
import com.opensocket.aievent.core.action.executor.audit.InMemoryAdapterExecutorAuditRepository;
import com.opensocket.aievent.core.action.executor.issue.IssueTrackingAdapterActionExecutor;
import com.opensocket.aievent.core.action.executor.issue.IssueVendorResolver;
import com.opensocket.aievent.core.callback.TaskCallbackRequest;
import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentFacade;
import com.opensocket.aievent.core.incident.IncidentObservationCommand;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class TaskTerminalIssueSyncIntegrationTest {

    @Test
    void terminalResultCallbackShouldCreateIssueActionExecuteRedmineAndLinkIncidentIssue() throws Exception {
        try (RecordingIssueServer redmine = new RecordingIssueServer(201, "{\"issue\":{\"id\":701}}")) {
            InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
            MutableIncidentFacade incidentFacade = new MutableIncidentFacade("incident-issue-sync");
            AdapterActionService actionService = new AdapterActionService(repository, incidentFacade, issueActionProperties());

            AdapterActionOrchestrationResult orchestration = actionService.evaluateAfterTaskCallback(
                    completedTask(), dispatchRequest(), resultCallback(), TaskCallbackType.RESULT);

            assertThat(orchestration.isTerminalTask()).isTrue();
            assertThat(orchestration.isIssueActionCreated()).isTrue();
            AdapterAction pendingIssueAction = repository.findByTaskId("task-issue-sync", 10).stream()
                    .filter(action -> action.getAdapterType() == AdapterType.ISSUE_TRACKING)
                    .findFirst()
                    .orElseThrow();
            assertThat(pendingIssueAction.getActionType()).isEqualTo(AdapterActionType.ISSUE_CREATE);
            assertThat(pendingIssueAction.getPayload()).containsEntry("issueCommentMode", "APPEND");
            assertThat(pendingIssueAction.getPayload()).containsEntry("adapterActionId", pendingIssueAction.getActionId());
            assertThat(pendingIssueAction.getPayload()).containsEntry("issueActionIdempotencyKey", pendingIssueAction.getIdempotencyKey());
            assertThat(String.valueOf(pendingIssueAction.getPayload().get("agentSummary"))).contains("Agent execution completed");

            AdapterActionExecutionService executionService = executionService(repository, incidentFacade, redmine.baseUrl());
            AdapterAction completed = executionService.execute(pendingIssueAction.getActionId());

            assertThat(completed.getStatus()).isEqualTo(AdapterActionStatus.COMPLETED);
            assertThat(completed.getResponseRef()).contains("\"vendor\":\"REDMINE\"");
            assertThat(completed.getResponseRef()).contains("\"issueId\":\"701\"");
            assertThat(completed.getResponseRef()).contains("\"idempotencyKey\":\"" + pendingIssueAction.getIdempotencyKey() + "\"");
            assertThat(incidentFacade.incident().getLinkedIssueId()).isEqualTo("REDMINE:701");
            assertThat(redmine.requests()).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.rawPath()).isEqualTo("/issues.json");
                assertThat(request.body()).contains("OpenDispatch Issue Context");
                assertThat(request.body()).contains("Agent Result Update");
                assertThat(request.body()).contains("task-issue-sync");
                assertThat(request.body()).contains("agent-issue-sync");
                assertThat(request.body()).contains("OpenDispatch: issue-action-idempotency-key=" + pendingIssueAction.getIdempotencyKey());
            });
        }
    }

    @Test
    void terminalResultCallbackShouldAppendCommentWhenIncidentAlreadyLinkedToIssue() throws Exception {
        try (RecordingIssueServer redmine = new RecordingIssueServer(200, "")) {
            InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
            MutableIncidentFacade incidentFacade = new MutableIncidentFacade("incident-linked-issue");
            incidentFacade.incident().setLinkedIssueId("REDMINE:701");
            AdapterActionService actionService = new AdapterActionService(repository, incidentFacade, issueActionProperties());

            AdapterActionOrchestrationResult orchestration = actionService.evaluateAfterTaskCallback(
                    completedTask("task-linked-issue", "incident-linked-issue"), dispatchRequest(), resultCallback(), TaskCallbackType.RESULT);

            AdapterAction updateAction = orchestration.getActions().stream()
                    .filter(action -> action.getAdapterType() == AdapterType.ISSUE_TRACKING)
                    .findFirst()
                    .orElseThrow();
            assertThat(updateAction.getActionType()).isEqualTo(AdapterActionType.ISSUE_UPDATE_COMMENT);
            assertThat(updateAction.getPayload()).containsEntry("linkedIssueId", "REDMINE:701");
            assertThat(updateAction.getPayload()).containsEntry("issueActionIdempotencyKey", updateAction.getIdempotencyKey());

            AdapterAction completed = executionService(repository, incidentFacade, redmine.baseUrl()).execute(updateAction.getActionId());

            assertThat(completed.getStatus()).isEqualTo(AdapterActionStatus.COMPLETED);
            assertThat(completed.getResponseRef()).contains("redmine-comment:701");
            assertThat(redmine.requests()).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("PUT");
                assertThat(request.rawPath()).isEqualTo("/issues/701.json");
                assertThat(request.body()).contains("Agent Result Update");
                assertThat(request.body()).contains("OpenDispatch: issue-action-idempotency-key=" + updateAction.getIdempotencyKey());
            });
        }
    }

    @Test
    void duplicateTerminalIssueCallbackShouldCreateOnlyOneExecutableIssueAction() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        MutableIncidentFacade incidentFacade = new MutableIncidentFacade("incident-duplicate-issue");
        incidentFacade.incident().setLinkedIssueId("REDMINE:701");
        AdapterActionService actionService = new AdapterActionService(repository, incidentFacade, issueActionProperties());

        TaskRecord task = completedTask("task-duplicate-issue", "incident-duplicate-issue");
        actionService.evaluateAfterTaskCallback(task, dispatchRequest(), resultCallback(), TaskCallbackType.RESULT);
        actionService.evaluateAfterTaskCallback(task, dispatchRequest(), resultCallback(), TaskCallbackType.RESULT);

        List<AdapterAction> issueActions = repository.findByTaskId("task-duplicate-issue", 10).stream()
                .filter(action -> action.getAdapterType() == AdapterType.ISSUE_TRACKING)
                .toList();

        assertThat(issueActions)
                .filteredOn(action -> action.getStatus() == AdapterActionStatus.PENDING)
                .hasSize(1);
        assertThat(issueActions)
                .filteredOn(action -> action.getStatus() == AdapterActionStatus.SUPPRESSED)
                .hasSize(1);
        assertThat(issueActions.stream().map(AdapterAction::getIdempotencyKey).distinct().count()).isEqualTo(2);
        assertThat(issueActions.stream()
                .filter(action -> action.getStatus() == AdapterActionStatus.PENDING)
                .findFirst()
                .orElseThrow()
                .getPayload())
                .containsKeys("adapterActionId", "issueActionIdempotencyKey", "issueCommentDedupeKey");
    }


    private AdapterActionProperties issueActionProperties() {
        AdapterActionProperties properties = new AdapterActionProperties();
        properties.getMcp().setEnabled(false);
        properties.getIssue().setEnabled(true);
        properties.getIssue().setCreateOnCompletedTask(true);
        properties.getIssue().setCreateOnFailedTask(true);
        properties.getIssue().setUpdateExistingIssueComment(true);
        properties.getIssue().setOneCreatePerIncident(true);
        properties.getIssue().setOneUpdatePerTask(true);
        return properties;
    }

    private AdapterActionExecutionService executionService(InMemoryAdapterActionRepository repository,
                                                          IncidentFacade incidentFacade,
                                                          String redmineBaseUrl) {
        AdapterActionExecutionProperties properties = new AdapterActionExecutionProperties();
        properties.setMode("embedded");
        properties.setEnabled(true);
        properties.setMaxAttempts(3);
        properties.getIssue().setDefaultVendor("REDMINE");
        properties.getIssue().getRedmine().setEnabled(true);
        properties.getIssue().getRedmine().setBaseUrl(redmineBaseUrl);
        properties.getIssue().getRedmine().setApiKey("redmine-token");
        properties.getIssue().getRedmine().setProjectId("MES-OPS");
        InMemoryAdapterExecutorAuditRepository auditRepository = new InMemoryAdapterExecutorAuditRepository();
        return new AdapterActionExecutionService(
                repository,
                List.of(new IssueTrackingAdapterActionExecutor(properties, new IssueVendorResolver(properties))),
                properties,
                new AdapterExecutorCircuitBreaker(properties),
                new AdapterExecutorAuditService(auditRepository, properties),
                incidentFacade);
    }

    private TaskRecord completedTask() {
        return completedTask("task-issue-sync", "incident-issue-sync");
    }

    private TaskRecord completedTask(String taskId, String incidentId) {
        TaskRecord task = new TaskRecord();
        task.setTaskId(taskId);
        task.setIncidentId(incidentId);
        task.setStatus(TaskStatus.COMPLETED);
        task.setSiteId("TPE");
        task.setPlantId("PLANT-A");
        task.setObjectType("EQUIPMENT");
        task.setObjectId("PUMP-7");
        task.setEventType("EQUIPMENT_ALARM");
        task.setErrorCode("ALM-9001");
        return task;
    }

    private DispatchRequest dispatchRequest() {
        DispatchRequest dispatch = new DispatchRequest();
        dispatch.setDispatchRequestId("dispatch-issue-sync");
        dispatch.setAssignmentId("assign-issue-sync");
        dispatch.setAgentId("agent-issue-sync");
        dispatch.setOwnerGatewayNodeId("gateway-node-001");
        dispatch.setAgentSessionId("session-agent-issue-sync");
        return dispatch;
    }

    private TaskCallbackRequest resultCallback() {
        TaskCallbackRequest callback = new TaskCallbackRequest();
        callback.setCallbackId("callback-issue-sync");
        callback.setResultStatus("SUCCESS");
        callback.setMessage("Agent execution completed with repair recommendation.");
        return callback;
    }

    private static final class MutableIncidentFacade implements IncidentFacade {
        private final Incident incident;

        private MutableIncidentFacade(String incidentId) {
            this.incident = new Incident();
            this.incident.setIncidentId(incidentId);
        }

        private Incident incident() {
            return incident;
        }

        @Override
        public Incident observe(IncidentObservationCommand command) {
            return incident;
        }

        @Override
        public Incident linkTaskIfAbsent(String incidentId, String taskId) {
            incident.setLinkedTaskId(taskId);
            return incident;
        }

        @Override
        public Incident linkIssueIfAbsent(String incidentId, String issueId) {
            if (incident.getLinkedIssueId() == null || incident.getLinkedIssueId().isBlank()) {
                incident.setLinkedIssueId(issueId);
            }
            return incident;
        }

        @Override
        public Optional<Incident> findById(String incidentId) {
            return incident.getIncidentId().equals(incidentId) ? Optional.of(incident) : Optional.empty();
        }
    }

    private static final class RecordingIssueServer implements AutoCloseable {
        private final HttpServer server;
        private final List<RecordedRequest> requests = new ArrayList<>();
        private final int status;
        private final String responseBody;

        private RecordingIssueServer(int status, String responseBody) throws IOException {
            this.status = status;
            this.responseBody = responseBody == null ? "" : responseBody;
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/", this::handle);
            this.server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private synchronized List<RecordedRequest> requests() {
            return List.copyOf(requests);
        }

        private void handle(HttpExchange exchange) throws IOException {
            byte[] body = exchange.getRequestBody().readAllBytes();
            synchronized (this) {
                requests.add(new RecordedRequest(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getRawPath(),
                        new String(body, java.nio.charset.StandardCharsets.UTF_8)));
            }
            byte[] response = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private record RecordedRequest(String method, String rawPath, String body) { }
}
