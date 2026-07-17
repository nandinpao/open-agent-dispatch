package com.opensocket.aievent.core.contract;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.assignment.AgentAssignmentService;
import com.opensocket.aievent.core.agent.contract.DispatchContractBootstrapRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractBootstrapResponse;
import com.opensocket.aievent.core.agent.contract.DispatchContractReadinessRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractReadinessResponse;
import com.opensocket.aievent.core.agent.contract.DispatchContractTestTaskRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractTestTaskResponse;
import com.opensocket.aievent.core.decision.DecisionEngine;
import com.opensocket.aievent.core.decision.EventIntakeDecisionResponse;
import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.task.evidence.TaskDispatchEvidenceView;
import com.opensocket.aievent.core.timeline.TaskDispatchEvidenceService;

@Service
public class DispatchContractTestTaskService {
    private final AgentAssignmentService assignmentService;
    private final DecisionEngine decisionEngine;
    private final TaskDispatchEvidenceService evidenceService;

    public DispatchContractTestTaskService(AgentAssignmentService assignmentService,
                                           DecisionEngine decisionEngine,
                                           TaskDispatchEvidenceService evidenceService) {
        this.assignmentService = assignmentService;
        this.decisionEngine = decisionEngine;
        this.evidenceService = evidenceService;
    }

    public DispatchContractTestTaskResponse createTestTask(DispatchContractTestTaskRequest raw) {
        DispatchContractTestTaskRequest request = raw == null ? new DispatchContractTestTaskRequest() : raw;
        String tenantId = normalizeRequired(request.getTenantId(), "tenantId");
        String sourceSystem = normalizeRequired(request.getSourceSystem(), "sourceSystem");
        String taskType = normalizeRequired(request.getTaskType(), "taskType");
        String agentId = blank(request.getAgentId()) ? null : request.getAgentId().trim();
        List<String> capabilities = normalizeList(request.getRequiredCapabilities());

        DispatchContractReadinessResponse readinessBefore = readiness(tenantId, sourceSystem, taskType, agentId, capabilities);
        DispatchContractBootstrapResponse bootstrap = null;
        DispatchContractReadinessResponse readinessAfter = readinessBefore;
        if (request.isEnsureContract() && !Boolean.TRUE.equals(readinessBefore.isReady())) {
            bootstrap = assignmentService.bootstrapDispatchContract(bootstrapRequest(request, tenantId, sourceSystem, taskType, agentId, capabilities));
            readinessAfter = readiness(tenantId, sourceSystem, taskType, agentId, capabilities);
        }

        EventIntakeRequest event = testEvent(request, tenantId, sourceSystem, taskType, agentId, capabilities);
        EventIntakeDecisionResponse intake = decisionEngine.ingest(event);

        TaskDispatchEvidenceView evidence = null;
        if (!blank(intake.taskId())) {
            try {
                evidence = evidenceService.evidence(intake.taskId(), 200);
            } catch (RuntimeException ignored) {
                evidence = null;
            }
        }

        DispatchContractTestTaskResponse response = new DispatchContractTestTaskResponse();
        response.setTenantId(tenantId);
        response.setSourceSystem(sourceSystem);
        response.setTaskType(taskType);
        response.setAgentId(agentId);
        response.setReadinessBefore(readinessBefore);
        response.setBootstrap(bootstrap);
        response.setReadinessAfter(readinessAfter);
        response.setEventDecision(intake);
        response.setTaskId(intake.taskId());
        response.setTaskCreated(intake.taskCreated());
        response.setAssignmentCreated(intake.assignmentCreated());
        response.setDispatchRequestCreated(intake.dispatchRequestCreated());
        response.setSelectedAgentId(intake.selectedAgentId());
        response.setEvidence(evidence);
        response.setStatus(status(intake, readinessAfter));
        response.setSummary(summary(intake, readinessAfter, bootstrap));
        response.setNextActions(nextActions(intake, readinessAfter));
        response.setDiagnostics(diagnostics(event, readinessBefore, readinessAfter, bootstrap));
        response.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return response;
    }

    private DispatchContractReadinessResponse readiness(String tenantId, String sourceSystem, String taskType, String agentId, List<String> capabilities) {
        DispatchContractReadinessRequest request = new DispatchContractReadinessRequest();
        request.setTenantId(tenantId);
        request.setSourceSystem(sourceSystem);
        request.setTaskType(taskType);
        request.setAgentId(agentId);
        request.setRequiredCapabilities(capabilities);
        return assignmentService.dispatchContractReadiness(request);
    }

    private DispatchContractBootstrapRequest bootstrapRequest(DispatchContractTestTaskRequest request,
                                                              String tenantId,
                                                              String sourceSystem,
                                                              String taskType,
                                                              String agentId,
                                                              List<String> capabilities) {
        DispatchContractBootstrapRequest bootstrap = new DispatchContractBootstrapRequest();
        bootstrap.setTenantId(tenantId);
        bootstrap.setSourceSystem(sourceSystem);
        bootstrap.setSourceSystemName(defaultText(request.getSourceSystemName(), title(sourceSystem)));
        bootstrap.setTaskType(taskType);
        bootstrap.setDisplayName(title(sourceSystem) + " " + title(taskType));
        bootstrap.setDescription("Generated by contract-aware dispatch simulator test task flow.");
        bootstrap.setDomain(sourceSystem);
        bootstrap.setRiskLevel(defaultText(request.getSeverity(), "MIDDLE"));
        bootstrap.setDefaultSeverity(defaultText(request.getSeverity(), "MIDDLE"));
        bootstrap.setCapabilityCode(capabilities.isEmpty() ? null : capabilities.get(0));
        bootstrap.setObjectType(defaultText(request.getObjectType(), "*"));
        bootstrap.setEventType(defaultText(request.getEventType(), "*"));
        bootstrap.setErrorCode(defaultText(request.getErrorCode(), "*"));
        bootstrap.setAgentId(agentId);
        bootstrap.setAssignAgent(request.isAssignAgent() && !blank(agentId));
        bootstrap.setApproveAgentQualification(request.isApproveAgentQualification());
        bootstrap.setApproveAgentCapability(request.isApproveAgentCapability());
        bootstrap.setActivate(request.isActivate());
        bootstrap.setOperatorId(defaultText(request.getOperatorId(), "dispatch-simulator"));
        return bootstrap;
    }

    private EventIntakeRequest testEvent(DispatchContractTestTaskRequest request,
                                         String tenantId,
                                         String sourceSystem,
                                         String taskType,
                                         String agentId,
                                         List<String> capabilities) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        EventIntakeRequest event = new EventIntakeRequest();
        event.setTenantId(tenantId);
        event.setSourceSystem(sourceSystem);
        event.setSiteId(defaultText(request.getSiteId(), "LOCAL"));
        event.setPlantId(defaultText(request.getPlantId(), "LOCAL-" + sourceSystem));
        event.setObjectType(defaultText(request.getObjectType(), "DISPATCH_CONTRACT_TEST"));
        event.setObjectId(defaultText(request.getObjectId(), sourceSystem + "-" + taskType + "-" + suffix));
        event.setEventType(defaultText(request.getEventType(), sourceSystem + "_" + taskType + "_TEST"));
        event.setErrorCode(defaultText(request.getErrorCode(), "DISPATCH_CONTRACT_TEST"));
        event.setSeverity(defaultText(request.getSeverity(), "CRITICAL"));
        event.setMessage(defaultText(request.getMessage(), "Contract-aware dispatch simulator test event for " + sourceSystem + " / " + taskType));
        event.setOccurredAt(now);
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (request.getAttributes() != null) {
            attributes.putAll(request.getAttributes());
        }
        attributes.put("dispatchContractTest", true);
        attributes.put("contractAwareSimulator", true);
        attributes.put("requestedTaskType", taskType);
        attributes.put("requiredCapabilities", capabilities);
        attributes.put("uniqueSuffix", suffix);
        if (!blank(agentId)) {
            attributes.put("preferredAgentId", agentId);
            attributes.put("testAgentId", agentId);
        }
        event.setAttributes(attributes);
        return event;
    }

    private Map<String, Object> diagnostics(EventIntakeRequest event,
                                            DispatchContractReadinessResponse before,
                                            DispatchContractReadinessResponse after,
                                            DispatchContractBootstrapResponse bootstrap) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("eventType", event.getEventType());
        diagnostics.put("objectId", event.getObjectId());
        diagnostics.put("readinessBefore", before == null ? null : before.getStatus());
        diagnostics.put("readinessAfter", after == null ? null : after.getStatus());
        diagnostics.put("bootstrapApplied", bootstrap != null);
        return diagnostics;
    }

    private String status(EventIntakeDecisionResponse intake, DispatchContractReadinessResponse readiness) {
        if (intake.dispatchRequestCreated()) return "DISPATCH_REQUEST_CREATED";
        if (intake.assignmentCreated()) return "ASSIGNED";
        if (intake.taskCreated()) return Boolean.TRUE.equals(readiness == null ? null : readiness.isReady()) ? "TASK_CREATED" : "TASK_CREATED_CONTRACT_BLOCKED";
        return "TASK_SUPPRESSED";
    }

    private String summary(EventIntakeDecisionResponse intake, DispatchContractReadinessResponse readiness, DispatchContractBootstrapResponse bootstrap) {
        if (intake.dispatchRequestCreated()) {
            return "Test Task created and dispatch request queued. Open the Evidence Timeline to verify delivery, ACK and RESULT.";
        }
        if (intake.assignmentCreated()) {
            return "Test Task created and assigned; dispatch request was not created. Open Evidence Timeline for the blocking stage.";
        }
        if (intake.taskCreated()) {
            return "Test Task created but no eligible assignment/dispatch was produced. " + defaultText(readiness == null ? null : readiness.getSummary(), "Review task evidence.");
        }
        if (bootstrap != null) {
            return "Contract was repaired, but the test event did not create a new task: " + defaultText(intake.taskDecisionReason(), intake.reason());
        }
        return "Test event did not create a task: " + defaultText(intake.taskDecisionReason(), intake.reason());
    }

    private List<String> nextActions(EventIntakeDecisionResponse intake, DispatchContractReadinessResponse readiness) {
        List<String> actions = new ArrayList<>();
        if (!Boolean.TRUE.equals(readiness == null ? null : readiness.isReady())) {
            actions.add("Repair Dispatch Contract");
        }
        if (!blank(intake.taskId())) {
            actions.add("Open Task Detail");
            actions.add("Open Dispatch Evidence Timeline");
        }
        if (intake.taskCreated() && !intake.dispatchRequestCreated()) {
            actions.add("Manual Retry Dispatch");
        }
        return actions;
    }

    private List<String> normalizeList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String item : raw) {
            String value = normalizeCode(item);
            if (!value.isBlank() && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private String normalizeRequired(String value, String field) {
        String normalized = normalizeCode(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private String defaultText(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    private String title(String value) {
        String normalized = value == null ? "" : value.replace('_', ' ').replace('-', ' ').toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (String part : normalized.split("\\s+")) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return out.isEmpty() ? "Dispatch Contract" : out.toString();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
