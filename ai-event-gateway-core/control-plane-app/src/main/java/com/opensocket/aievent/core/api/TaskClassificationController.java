package com.opensocket.aievent.core.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.task.TaskA2AClassificationFlowContract;
import com.opensocket.aievent.core.task.TaskClassificationRequest;
import com.opensocket.aievent.core.task.TaskClassificationResult;
import com.opensocket.aievent.core.task.TaskClassificationService;

/** Classification callback API for TRIAGE Agent -> RESOLUTION Task handoff. */
@RestController
@RequestMapping
public class TaskClassificationController {
    private static final Logger log = LoggerFactory.getLogger(TaskClassificationController.class);
    private final TaskClassificationService classificationService;

    public TaskClassificationController(TaskClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @GetMapping("/admin/tasks/a2a-classification/contract")
    public TaskA2AClassificationFlowContract a2aClassificationFlowContract() {
        return classificationService.a2aClassificationFlowContract();
    }

    @PostMapping("/api/agent/tasks/{taskId}/classification-result")
    public TaskClassificationResult agentClassificationResult(@PathVariable String taskId,
                                                              @RequestBody(required = false) TaskClassificationRequest request) {
        log.info("task_classification_result_http_received path=agent taskId={} eventType={} recommendedPoolCode={}",
                taskId, request == null ? null : request.getEventType(), request == null ? null : request.getRecommendedPoolCode());
        return classificationService.submitClassificationResult(taskId, request);
    }

    @PostMapping("/internal/tasks/{taskId}/classification-result")
    public TaskClassificationResult internalClassificationResult(@PathVariable String taskId,
                                                                 @RequestBody(required = false) TaskClassificationRequest request) {
        log.info("task_classification_result_http_received path=internal taskId={} eventType={} recommendedPoolCode={}",
                taskId, request == null ? null : request.getEventType(), request == null ? null : request.getRecommendedPoolCode());
        return classificationService.submitClassificationResult(taskId, request);
    }
}
