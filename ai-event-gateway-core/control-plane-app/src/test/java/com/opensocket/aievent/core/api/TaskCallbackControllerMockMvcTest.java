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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.opensocket.aievent.core.callback.DispatchRecoveryService;
import com.opensocket.aievent.core.callback.TaskCallbackProperties;
import com.opensocket.aievent.core.callback.TaskCallbackRequest;
import com.opensocket.aievent.core.callback.TaskCallbackResult;
import com.opensocket.aievent.core.callback.TaskCallbackService;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;

/** Focused MVC contract tests for Core task callback endpoints. */
class TaskCallbackControllerMockMvcTest {
    private TaskCallbackService callbackService;
    private ExecutionOperationalQuery queryService;
    private DispatchRecoveryService recoveryService;
    private TaskCallbackProperties properties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        callbackService = mock(TaskCallbackService.class);
        queryService = mock(ExecutionOperationalQuery.class);
        recoveryService = mock(DispatchRecoveryService.class);
        properties = new TaskCallbackProperties();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TaskCallbackController(callbackService, queryService, recoveryService, properties))
                .setControllerAdvice(new ApiExceptionHandler(), new StandardApiResponseAdvice())
                .build();
    }

    @Test
    void shouldWrapAckCallbackResultAndDelegateToService() throws Exception {
        TaskCallbackResult result = acceptedResult("callback-001", "task-001", "dispatch-001", "ACK");
        when(callbackService.ack(eq("task-001"), any(TaskCallbackRequest.class))).thenReturn(result);

        mockMvc.perform(post("/internal/control-plane/tasks/task-001/ack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callbackId":"callback-001","dispatchRequestId":"dispatch-001","agentId":"agent-001","attemptNo":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.message", is("Success")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.callbackId", is("callback-001")))
                .andExpect(jsonPath("$.data.taskId", is("task-001")))
                .andExpect(jsonPath("$.data.dispatchRequestId", is("dispatch-001")))
                .andExpect(jsonPath("$.data.callbackType", is("ACK")))
                .andExpect(jsonPath("$.data.accepted", is(true)));

        verify(callbackService).ack(eq("task-001"), any(TaskCallbackRequest.class));
    }

    @Test
    void shouldExposeCallbackMetadataInStandardEnvelope() throws Exception {
        when(queryService.callbackStoreMode()).thenReturn("MEMORY");

        mockMvc.perform(get("/internal/control-plane/tasks/callbacks/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.store", is("MEMORY")))
                .andExpect(jsonPath("$.data.idempotencyEnabled", is(true)))
                .andExpect(jsonPath("$.data.replayProtectionEnabled", is(true)))
                .andExpect(jsonPath("$.data.requireDispatchToken", is(true)))
                .andExpect(jsonPath("$.data.dispatchTimeout", is("PT10M")))
                .andExpect(jsonPath("$.data.maxAttempts", is(3)));
    }

    @Test
    void shouldReturnBusinessErrorEnvelopeWhenCallbackIsRejected() throws Exception {
        when(callbackService.error(eq("task-002"), any(TaskCallbackRequest.class)))
                .thenThrow(new IllegalArgumentException("dispatch token is required"));

        mockMvc.perform(post("/internal/control-plane/tasks/task-002/error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callbackId\":\"callback-err\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.message", is("dispatch token is required")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private TaskCallbackResult acceptedResult(String callbackId, String taskId, String dispatchRequestId, String callbackType) {
        TaskCallbackResult result = new TaskCallbackResult();
        result.setCallbackId(callbackId);
        result.setTaskId(taskId);
        result.setDispatchRequestId(dispatchRequestId);
        result.setCallbackType(callbackType);
        result.setAccepted(true);
        result.setDuplicate(false);
        result.setHttpStatus(200);
        result.setMessage("accepted");
        return result;
    }
}
