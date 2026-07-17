package com.opensocket.aievent.core.contract;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.assignment.AgentAssignmentService;
import com.opensocket.aievent.core.agent.contract.DispatchContractReadinessRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractReadinessResponse;
import com.opensocket.aievent.core.agent.contract.DispatchContractTraceRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractTraceResponse;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;

@Service
public class DispatchContractTraceService {
    private final AgentAssignmentService assignmentService;
    private final TaskRepository taskRepository;

    public DispatchContractTraceService(AgentAssignmentService assignmentService,
                                        ObjectProvider<TaskRepository> taskRepository) {
        this.assignmentService = assignmentService;
        this.taskRepository = taskRepository == null ? null : taskRepository.getIfAvailable();
    }

    public DispatchContractTraceResponse trace(DispatchContractTraceRequest request) {
        DispatchContractTraceRequest body = request == null ? new DispatchContractTraceRequest() : request;
        TaskRecord task = loadTask(body.getTaskId());
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (body.getAttributes() != null) {
            attributes.putAll(body.getAttributes());
        }

        String tenantId = firstNonBlank(body.getTenantId(), task == null ? null : task.getTenantId(), "default");
        String sourceSystem = normalize(firstNonBlank(body.getSourceSystem(), task == null ? null : task.getSourceSystem(), stringValue(attributes.get("sourceSystem")), stringValue(attributes.get("source_system"))));
        String taskType = normalize(firstNonBlank(body.getTaskType(), task == null ? null : task.getEffectiveTaskTypeCode(), stringValue(attributes.get("taskType")), stringValue(attributes.get("taskTypeCode"))));
        String agentId = firstNonBlank(body.getAgentId(), stringValue(attributes.get("agentId")));

        List<String> requiredCapabilities = mergeCapabilities(body.getRequiredCapabilities(),
                task == null ? List.of() : task.getRequiredCapabilities(),
                List.of(),
                attributes.get("requiredCapabilities"),
                attributes.get("requiredCapability"));

        DispatchContractReadinessResponse readiness = null;
        if (!blank(sourceSystem) && !blank(taskType)) {
            DispatchContractReadinessRequest readinessRequest = new DispatchContractReadinessRequest();
            readinessRequest.setTenantId(tenantId);
            readinessRequest.setSourceSystem(sourceSystem);
            readinessRequest.setTaskType(taskType);
            readinessRequest.setAgentId(agentId);
            readinessRequest.setRequiredCapabilities(requiredCapabilities);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("trace", true);
            metadata.put("authority", "DISPATCH_CONTRACT_TRACE");
            String tracedTaskId = firstNonBlank(body.getTaskId(), task == null ? null : task.getTaskId());
            if (!blank(tracedTaskId)) {
                metadata.put("taskId", tracedTaskId);
            }
            readinessRequest.setMetadata(metadata);
            readiness = assignmentService.dispatchContractReadiness(readinessRequest);
        }

        DispatchContractTraceResponse response = new DispatchContractTraceResponse();
        response.setTenantId(tenantId);
        response.setTaskId(firstNonBlank(body.getTaskId(), task == null ? null : task.getTaskId()));
        response.setSourceSystem(sourceSystem);
        response.setTaskType(taskType);
        response.setAgentId(agentId);
        response.setCapabilityResolution(null);
        response.setReadiness(readiness);
        response.setRequiredCapabilities(requiredCapabilities);
        response.setChecks(readiness == null ? List.of() : readiness.getChecks());
        response.setReady(readiness != null && readiness.isReady());
        response.setStatus(readiness == null ? "BLOCKED" : readiness.getStatus());
        response.setSummary(summary(sourceSystem, taskType, readiness));
        response.setFirstBlockingCode(readiness == null ? "TASK_CONTRACT_IDENTITY_MISSING" : readiness.getFirstBlockingCode());
        response.setFirstBlockingReason(readiness == null ? "sourceSystem/taskType could not be resolved." : readiness.getFirstBlockingReason());
        response.setDiagnostics(diagnostics(body, task, readiness, requiredCapabilities));
        response.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return response;
    }

    private TaskRecord loadTask(String taskId) {
        if (blank(taskId) || taskRepository == null) {
            return null;
        }
        return taskRepository.findById(taskId).orElse(null);
    }

    @SafeVarargs
    private final List<String> mergeCapabilities(List<String> first, List<String> second, List<String> third, Object... raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        addCapabilities(values, first);
        addCapabilities(values, second);
        addCapabilities(values, third);
        if (raw != null) {
            for (Object item : raw) {
                addCapabilities(values, item);
            }
        }
        return new ArrayList<>(values);
    }

    private void addCapabilities(LinkedHashSet<String> target, Object raw) {
        if (target == null || raw == null) {
            return;
        }
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                addCapabilities(target, item);
            }
            return;
        }
        String text = String.valueOf(raw);
        if (blank(text)) {
            return;
        }
        for (String item : text.split(",")) {
            String normalized = normalize(item);
            if (!blank(normalized)) {
                target.add(normalized);
            }
        }
    }

    private Map<String, Object> diagnostics(DispatchContractTraceRequest request,
                                            TaskRecord task,
                                            DispatchContractReadinessResponse readiness,
                                            List<String> requiredCapabilities) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("authority", "DISPATCH_FLOW_DIRECT_TRACE");
        values.put("inputTaskId", request == null ? null : request.getTaskId());
        values.put("taskFound", task != null);
        values.put("taskTypeCode", task == null ? null : task.getTaskTypeCode());
        values.put("capabilitySource", "DISPATCH_FLOW_REQUIRED_CAPABILITIES");
        values.put("requiredCapabilities", requiredCapabilities == null ? List.of() : requiredCapabilities);
        values.put("readinessReady", readiness != null && readiness.isReady());
        values.put("readinessStatus", readiness == null ? null : readiness.getStatus());
        values.put("firstBlockingCode", readiness == null ? null : readiness.getFirstBlockingCode());
        return values;
    }

    private String summary(String sourceSystem, String taskType, DispatchContractReadinessResponse readiness) {
        if (blank(sourceSystem) || blank(taskType)) {
            return "Dispatch contract trace is blocked because sourceSystem/taskType could not be resolved.";
        }
        if (readiness == null) {
            return "Dispatch contract identity resolved, but readiness was not evaluated.";
        }
        return "Dispatch contract trace resolved " + sourceSystem + " / " + taskType + ": " + readiness.getSummary();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!blank(value)) return value;
        }
        return null;
    }

    private String normalize(String value) {
        return blank(value) ? null : value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
