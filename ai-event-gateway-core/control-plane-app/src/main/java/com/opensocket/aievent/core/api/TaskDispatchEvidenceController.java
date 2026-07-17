package com.opensocket.aievent.core.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.task.evidence.TaskDispatchEvidenceView;
import com.opensocket.aievent.core.task.evidence.TaskRuntimeVerificationView;
import com.opensocket.aievent.core.timeline.TaskDispatchEvidenceService;
import com.opensocket.aievent.core.timeline.TaskRuntimeVerificationService;

@RestController
@RequestMapping("/admin/tasks")
public class TaskDispatchEvidenceController {
    private final TaskDispatchEvidenceService evidenceService;
    private final TaskRuntimeVerificationService runtimeVerificationService;

    public TaskDispatchEvidenceController(TaskDispatchEvidenceService evidenceService,
                                          TaskRuntimeVerificationService runtimeVerificationService) {
        this.evidenceService = evidenceService;
        this.runtimeVerificationService = runtimeVerificationService;
    }

    @GetMapping("/{taskId}/dispatch-evidence")
    public TaskDispatchEvidenceView dispatchEvidence(@PathVariable String taskId,
                                                     @RequestParam(defaultValue = "200") int limit) {
        try {
            return evidenceService.evidence(taskId, limit);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/{taskId}/runtime-verification")
    public TaskRuntimeVerificationView runtimeVerification(@PathVariable String taskId,
                                                           @RequestParam(defaultValue = "90") int timeoutSeconds,
                                                           @RequestParam(defaultValue = "200") int limit) {
        try {
            return runtimeVerificationService.verify(taskId, timeoutSeconds, limit);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }


}
