package com.opensocket.aievent.core.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.opensocket.aievent.core.callback.CallbackInboxService;
import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService;
import com.opensocket.aievent.core.dispatch.DispatchAttemptLedgerService;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestService;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.dispatch.TaskFailureQueueService;
import com.opensocket.aievent.core.lifecycle.TaskLifecycleService;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.timeline.AdminFailureQueueResponse;
import com.opensocket.aievent.core.timeline.DispatchTimelineService;

/** Focused MVC contract tests for the Core Admin task facade. */
class CoreAdminTaskFacadeControllerMockMvcTest {
    private TaskOperationalQuery taskQuery;
    private TaskLifecycleService taskLifecycleService;
    private ExecutionOperationalQuery executionQuery;
    private DispatchRequestService dispatchRequestService;
    private DispatchAttemptHistoryService attemptHistoryService;
    private DispatchAttemptLedgerService dispatchAttemptLedgerService;
    private CallbackInboxService callbackInboxService;
    private TaskFailureQueueService failureQueueService;
    private DispatchTimelineService timelineService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskQuery = mock(TaskOperationalQuery.class);
        taskLifecycleService = mock(TaskLifecycleService.class);
        executionQuery = mock(ExecutionOperationalQuery.class);
        dispatchRequestService = mock(DispatchRequestService.class);
        attemptHistoryService = mock(DispatchAttemptHistoryService.class);
        dispatchAttemptLedgerService = mock(DispatchAttemptLedgerService.class);
        callbackInboxService = mock(CallbackInboxService.class);
        failureQueueService = mock(TaskFailureQueueService.class);
        timelineService = mock(DispatchTimelineService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CoreAdminTaskFacadeController(
                        taskQuery,
                        taskLifecycleService,
                        executionQuery,
                        dispatchRequestService,
                        attemptHistoryService,
                        dispatchAttemptLedgerService,
                        callbackInboxService,
                        failureQueueService,
                        timelineService))
                .setControllerAdvice(new ApiExceptionHandler(), new StandardApiResponseAdvice())
                .build();
    }

    @Test
    void shouldWrapTaskLookupInStandardEnvelope() throws Exception {
        when(taskQuery.findTask("task-001")).thenReturn(Optional.of(task("task-001", TaskStatus.QUEUED)));

        mockMvc.perform(get("/admin/tasks/task-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.taskId", is("task-001")))
                .andExpect(jsonPath("$.data.status", is("QUEUED")));
    }

    @Test
    void shouldClampFailureQueueLimitBeforeDelegating() throws Exception {
        AdminFailureQueueResponse response = new AdminFailureQueueResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                0,
                Map.of(),
                List.of());
        when(timelineService.failureQueue(500)).thenReturn(response);

        mockMvc.perform(get("/admin/tasks/failure-queue").param("limit", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.total", is(0)));

        verify(timelineService).failureQueue(500);
    }

    @Test
    void shouldWrapManualRetryCommandResult() throws Exception {
        TaskRecord retried = task("task-002", TaskStatus.QUEUED);
        when(failureQueueService.manualRetry(eq("task-002"), eq("operator retry"), any(OffsetDateTime.class)))
                .thenReturn(retried);

        mockMvc.perform(post("/admin/tasks/task-002/manual-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"operator retry\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.success", is(true)))
                .andExpect(jsonPath("$.data.message", is("Manual retry requested: task-002")))
                .andExpect(jsonPath("$.data.payload.taskId", is("task-002")))
                .andExpect(jsonPath("$.data.payload.status", is("QUEUED")));
    }

    @Test
    void shouldRetryLatestDispatchRequestForTask() throws Exception {
        DispatchRequest older = dispatch("dispatch-old", "task-003", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        DispatchRequest latest = dispatch("dispatch-latest", "task-003", OffsetDateTime.now(ZoneOffset.UTC));
        DispatchRequest retried = dispatch("dispatch-latest", "task-003", OffsetDateTime.now(ZoneOffset.UTC));
        retried.setStatus(DispatchRequestStatus.RETRY_WAITING);
        when(executionQuery.findDispatchRequestsByTask("task-003", 100)).thenReturn(List.of(older, latest));
        when(dispatchRequestService.retry("dispatch-latest", "Retry requested from Admin UI for task task-003", false, true))
                .thenReturn(retried);

        mockMvc.perform(post("/admin/tasks/task-003/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.success", is(true)))
                .andExpect(jsonPath("$.data.payload.dispatchRequestId", is("dispatch-latest")))
                .andExpect(jsonPath("$.data.payload.status", is("RETRY_WAITING")));
    }


    @Test
    void shouldBuildCaseTimelineFromPersistedRequiredCapabilitiesWithoutTaskModeField() throws Exception {
        TaskRecord task = task("task-capability", TaskStatus.QUEUED);
        task.setMatchedFlowId("flow-001");
        task.setMatchedRuleId("rule-001");
        task.setRoutingPath("FLOW_RULE");
        task.setRequiredCapabilities(List.of("CAP_DOCUMENT_ANALYSIS"));
        task.setRequestedSkill(null);
        when(taskQuery.findTask("task-capability")).thenReturn(Optional.of(task));
        when(executionQuery.findDispatchRequestsByTask("task-capability", 1))
                .thenReturn(List.of(dispatch("dispatch-capability", "task-capability", OffsetDateTime.now(ZoneOffset.UTC))));

        mockMvc.perform(get("/admin/tasks/task-capability/case-timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.steps[2].status", is("PASS")))
                .andExpect(jsonPath("$.data.steps[2].message", is("Capability is optional; TaskRecord.requiredCapabilities is the persisted task-level requirement evidence.")));
    }

    @Test
    void shouldReportMalformedPersistedCapabilityRequirementWithoutUsingMissingTaskModeGetter() throws Exception {
        TaskRecord task = task("task-malformed-capability", TaskStatus.QUEUED);
        task.setMatchedFlowId("flow-001");
        task.setMatchedRuleId("rule-001");
        task.setRoutingPath("FLOW_RULE");
        task.setRequiredCapabilities(List.of(""));
        when(taskQuery.findTask("task-malformed-capability")).thenReturn(Optional.of(task));
        when(executionQuery.findDispatchRequestsByTask("task-malformed-capability", 1))
                .thenReturn(List.of(dispatch("dispatch-malformed", "task-malformed-capability", OffsetDateTime.now(ZoneOffset.UTC))));

        mockMvc.perform(get("/admin/tasks/task-malformed-capability/case-timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failureStage", is("REQUIRED_CAPABILITY_MISSING")))
                .andExpect(jsonPath("$.data.steps[2].status", is("BLOCKED")));
    }

    @Test
    void shouldReturnBusinessErrorEnvelopeWhenTaskIsMissing() throws Exception {
        when(taskQuery.findTask("missing-task")).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/tasks/missing-task"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.message", is("Task not found: missing-task")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private TaskRecord task(String taskId, TaskStatus status) {
        TaskRecord task = new TaskRecord();
        task.setTaskId(taskId);
        task.setStatus(status);
        task.setTenantId("tenant-test");
        task.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return task;
    }

    private DispatchRequest dispatch(String dispatchRequestId, String taskId, OffsetDateTime updatedAt) {
        DispatchRequest dispatch = new DispatchRequest();
        dispatch.setDispatchRequestId(dispatchRequestId);
        dispatch.setTaskId(taskId);
        dispatch.setAgentId("agent-001");
        dispatch.setStatus(DispatchRequestStatus.FAILED);
        dispatch.setCreatedAt(updatedAt.minusMinutes(1));
        dispatch.setUpdatedAt(updatedAt);
        return dispatch;
    }
}
