package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.action.AdapterActionFacade;
import com.opensocket.aievent.core.action.AdapterActionProperties;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.agent.AgentControlOperationalQuery;
import com.opensocket.aievent.core.config.CoreDecisionProperties;
import com.opensocket.aievent.core.config.CoreDeploymentProperties;
import com.opensocket.aievent.core.callback.TaskCallbackProperties;
import com.opensocket.aievent.core.dispatch.DispatchProperties;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.fingerprint.FingerprintPolicyProperties;
import com.opensocket.aievent.core.incident.IncidentOperationalQuery;
import com.opensocket.aievent.core.integration.IntegrationEventOperationalQuery;
import com.opensocket.aievent.core.integration.IntegrationEventProperties;
import com.opensocket.aievent.core.kernel.CoreVersion;
import com.opensocket.aievent.core.lifecycle.LifecycleProperties;
import com.opensocket.aievent.core.observability.ObservabilityProperties;
import com.opensocket.aievent.core.processing.EventProcessingOperationalQuery;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskOrchestrationProperties;
import com.opensocket.aievent.core.task.TaskDispatchRecoveryProperties;

@RestController
@RequestMapping("/api/core")
public class CoreStatusController {
    private final EventProcessingOperationalQuery eventProcessing;
    private final IncidentOperationalQuery incidents;
    private final TaskOperationalQuery tasks;
    private final AgentControlOperationalQuery agents;
    private final ExecutionOperationalQuery execution;
    private final AdapterActionFacade adapterActions;
    private final CoreDecisionProperties properties;
    private final CoreDeploymentProperties deploymentProperties;
    private final IntegrationEventOperationalQuery integrationEvents;
    private final IntegrationEventProperties integrationEventProperties;
    private final TaskOrchestrationProperties taskProperties;
    private final TaskDispatchRecoveryProperties taskDispatchRecoveryProperties;
    private final RoutingProperties routingProperties;
    private final DispatchProperties dispatchProperties;
    private final TaskCallbackProperties taskCallbackProperties;
    private final AdapterActionProperties adapterActionProperties;
    private final AdapterActionExecutionProperties adapterActionExecutionProperties;
    private final FingerprintPolicyProperties fingerprintPolicyProperties;
    private final LifecycleProperties lifecycleProperties;
    private final ObservabilityProperties observabilityProperties;

    public CoreStatusController(EventProcessingOperationalQuery eventProcessing,
                                IncidentOperationalQuery incidents,
                                TaskOperationalQuery tasks,
                                AgentControlOperationalQuery agents,
                                ExecutionOperationalQuery execution,
                                AdapterActionFacade adapterActions,
                                CoreDecisionProperties properties,
                                CoreDeploymentProperties deploymentProperties,
                                IntegrationEventOperationalQuery integrationEvents,
                                IntegrationEventProperties integrationEventProperties,
                                TaskOrchestrationProperties taskProperties,
                                TaskDispatchRecoveryProperties taskDispatchRecoveryProperties,
                                RoutingProperties routingProperties,
                                DispatchProperties dispatchProperties,
                                TaskCallbackProperties taskCallbackProperties,
                                AdapterActionProperties adapterActionProperties,
                                AdapterActionExecutionProperties adapterActionExecutionProperties,
                                FingerprintPolicyProperties fingerprintPolicyProperties,
                                LifecycleProperties lifecycleProperties,
                                ObservabilityProperties observabilityProperties) {
        this.eventProcessing = eventProcessing;
        this.incidents = incidents;
        this.tasks = tasks;
        this.agents = agents;
        this.execution = execution;
        this.adapterActions = adapterActions;
        this.properties = properties;
        this.deploymentProperties = deploymentProperties;
        this.integrationEvents = integrationEvents;
        this.integrationEventProperties = integrationEventProperties;
        this.taskProperties = taskProperties;
        this.taskDispatchRecoveryProperties = taskDispatchRecoveryProperties;
        this.routingProperties = routingProperties;
        this.dispatchProperties = dispatchProperties;
        this.taskCallbackProperties = taskCallbackProperties;
        this.adapterActionProperties = adapterActionProperties;
        this.adapterActionExecutionProperties = adapterActionExecutionProperties;
        this.fingerprintPolicyProperties = fingerprintPolicyProperties;
        this.lifecycleProperties = lifecycleProperties;
        this.observabilityProperties = observabilityProperties;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("app", "ai-event-gateway-core");
        status.put("version", CoreVersion.CURRENT);
        status.put("deploymentMode", deploymentProperties.getMode().name());
        status.put("dedupStore", eventProcessing.dedupStoreMode());
        status.put("dedupSnapshotStore", eventProcessing.dedupSnapshotStoreMode());
        status.put("incidentStore", incidents.incidentStoreMode());
        status.put("incidentSummaryStore", incidents.occurrenceSummaryStoreMode());
        status.put("taskStore", tasks.taskStoreMode());
        status.put("gatewayNodeStore", agents.gatewayStoreMode());
        status.put("agentDirectoryStore", agents.agentStoreMode());
        status.put("assignmentStore", tasks.assignmentStoreMode());
        status.put("routingDecisionStore", tasks.routingStoreMode());
        status.put("routingAssignmentEnabled", routingProperties.isAssignmentEnabled());
        status.put("routingMinimumScore", routingProperties.getMinimumScore());
        status.put("routingPoisonAgentExclusionEnabled", routingProperties.isPoisonAgentExclusionEnabled());
        status.put("routingPoisonAgentFailureThreshold", routingProperties.getPoisonAgentFailureThreshold());
        status.put("routingLoadAwareScoringEnabled", routingProperties.isLoadAwareScoringEnabled());
        status.put("routingZeroSpecialCaseRuntimeEnabled", routingProperties.isZeroSpecialCaseRuntimeEnabled());
        status.put("routingPersistedLegacyEvidenceRecoveryEnabled", routingProperties.isPersistedLegacyEvidenceRecoveryEnabled());
        status.put("routingNewWorkFailClosed", routingProperties.isZeroSpecialCaseRuntimeEnabled()
                && routingProperties.isFlowRuleRoutingEnabled()
                && !routingProperties.isFlowRuleLegacyFallbackEnabled());
        status.put("routingSkillVersionCompatibilityEnabled", routingProperties.isSkillVersionCompatibilityEnabled());
        status.put("routingSkillVersionEnforced", routingProperties.isSkillVersionEnforced());
        status.put("routingExplainabilityEnabled", true);
        status.put("routingExplainabilityFields", List.of("userFacingError", "decisionReason", "candidates", "scoreBreakdown", "poisonAgentExcluded", "skillVersionReason"));
        status.put("agentRemediationWorkflowEnabled", true);
        status.put("agentRemediationApprovalWorkflowEnabled", true);
        status.put("agentRemediationWorkflowStatuses", List.of("PENDING_APPROVAL", "APPROVED", "REJECTED", "CANCELLED", "EXECUTED"));
        status.put("agentRemediationActionExecutionStatuses", List.of("PENDING", "RUNNING", "SUCCEEDED", "SKIPPED", "FAILED"));
        status.put("agentRemediationWorkflowExecutionIntegrationEnabled", true);
        status.put("agentRemediationWorkflowGuardrails", List.of("highRiskRequiresApproval", "riskAcknowledgementRequired", "executionHistoryRecorded", "rollbackSuggestionRequired", "persistentRepository", "haOptimisticTransition", "executionFailureKeepsApproved", "dryRunSupported", "actionLevelIdempotency", "partialRetrySafe", "perActionAttemptCounter", "workflowExecutionLease", "staleLeaseTakeover", "scheduledStaleLeaseRecovery", "staleLeaseAdminQueue"));
        status.put("agentRemediationWorkflowRepository", "POSTGRESQL_MYBATIS");
        status.put("agentRemediationWorkflowHaReady", true);
        status.put("agentRemediationWorkflowActionLevelIdempotencyEnabled", true);
        status.put("agentRemediationWorkflowExactlyOnceGuardrails", List.of("uniqueIdempotencyKey", "conditionalActionClaim", "skipSucceededActions", "retryFailedActions", "partialSuccessContinuation", "workflowLeaseBeforeActionClaim"));
        status.put("agentRemediationWorkflowExecutionLeaseEnabled", true);
        status.put("agentRemediationWorkflowExecutionLeaseTtlSeconds", 600);
        status.put("agentRemediationWorkflowExecutionLeaseFields", List.of("executionLeaseOwner", "executionLeaseAcquiredAt", "executionLeaseExpiresAt", "executionLeaseRemainingSeconds", "executionLeaseActive"));
        status.put("agentRemediationWorkflowStaleLeaseRecoveryEnabled", true);
        status.put("agentRemediationWorkflowStaleLeaseRecoveryMode", "P11_SCHEDULED_REAPER_AND_ADMIN_QUEUE");
        status.put("agentRemediationWorkflowStaleLeaseRecoveryEvent", "EXECUTION_LEASE_STALE_RECOVERED");
        status.put("agentRemediationWorkflowStaleLeaseQueueEndpoints", List.of(
                "GET /admin/remediation/workflow-leases/stale",
                "GET /admin/remediation/workflow-leases/recovered",
                "POST /admin/remediation/workflow-leases/recover-stale"));
        status.put("agentRemediationWorkflowMetricsEnabled", observabilityProperties.getRemediationWorkflowMetrics().isEnabled());
        status.put("agentRemediationWorkflowMetricsMode", "P12_MICROMETER_PROMETHEUS_ALERTING");
        status.put("agentRemediationWorkflowMetricNames", List.of(
                "aeg.core.remediation.workflows.created.total",
                "aeg.core.remediation.workflows.decisions.total",
                "aeg.core.remediation.workflows.approval.latency",
                "aeg.core.remediation.workflows.executions.total",
                "aeg.core.remediation.workflow.actions.executions.total",
                "aeg.core.remediation.workflow.execution.lease.events.total",
                "aeg.core.remediation.workflow.stale_leases.recovery_runs.total",
                "aeg.core.remediation.workflow.stale_leases.recovered.total"));
        status.put("agentRemediationWorkflowMetricsGuardrails", List.of("lowCardinalityLabels", "noAgentIdLabel", "noWorkflowIdLabel", "noOperatorIdLabel", "noIdempotencyKeyLabel"));
        status.put("agentRemediationWorkflowApprovalLatencyWarning", observabilityProperties.getRemediationWorkflowMetrics().getApprovalLatencyWarning().toString());
        status.put("agentRemediationWorkflowApprovalLatencyCritical", observabilityProperties.getRemediationWorkflowMetrics().getApprovalLatencyCritical().toString());
        status.put("agentRemediationWorkflowActionFailureRatioWarning", observabilityProperties.getRemediationWorkflowMetrics().getActionFailureRatioWarning());
        status.put("agentRemediationWorkflowActionFailureRatioCritical", observabilityProperties.getRemediationWorkflowMetrics().getActionFailureRatioCritical());
        status.put("agentRemediationActions", List.of("CLEAR_RUNTIME_BACKOFF", "DISCONNECT_ALL_RUNTIME_SESSIONS", "SUSPEND_AGENT", "SYNC_APPROVED_SKILLS_AND_CAPABILITIES", "REVIEW_OR_PUBLISH_SKILL_VERSION"));
        status.put("agentRemediationExecutableActions", List.of("CLEAR_RUNTIME_BACKOFF", "DISCONNECT_ALL_RUNTIME_SESSIONS", "SUSPEND_AGENT", "SYNC_APPROVED_SKILLS_AND_CAPABILITIES"));
        status.put("dispatchRequestStore", execution.dispatchStoreMode());
        status.put("dispatchRequestCreationEnabled", dispatchProperties.isRequestCreationEnabled());
        status.put("dispatchReviewMode", dispatchProperties.getReviewMode());
        status.put("dispatchRequireAssignableAgent", dispatchProperties.isRequireAssignableAgent());
        status.put("dispatchClientEnabled", dispatchProperties.getClient().isEnabled());
        status.put("dispatchClientAutoExecuteApproved", dispatchProperties.getClient().isAutoExecuteApproved());
        status.put("dispatchClientDefaultGatewayBaseUrl", dispatchProperties.getClient().getDefaultGatewayBaseUrl());
        status.put("taskCallbackStore", execution.callbackStoreMode());
        status.put("taskCallbackIdempotencyEnabled", taskCallbackProperties.isIdempotencyEnabled());
        status.put("taskCallbackRequireDispatchToken", taskCallbackProperties.isRequireDispatchToken());
        status.put("taskCallbackEnforceStateTransition", taskCallbackProperties.isEnforceStateTransition());
        status.put("taskCallbackRejectOldAttemptCallbacks", taskCallbackProperties.isRejectOldAttemptCallbacks());
        status.put("taskCallbackRequireAttemptNo", taskCallbackProperties.isRequireAttemptNo());
        status.put("taskCallbackEnforceGatewayAndAgentIdentity", taskCallbackProperties.isEnforceGatewayAndAgentIdentity());
        status.put("dispatchRecoveryTimeoutEnabled", taskCallbackProperties.getRecovery().isTimeoutEnabled());
        status.put("dispatchRecoveryRetryEnabled", taskCallbackProperties.getRecovery().isRetryEnabled());
        status.put("dispatchRetryEnabled", dispatchProperties.getRetry().isEnabled());
        status.put("dispatchRetryMaxAttempts", dispatchProperties.getRetry().getMaxAttempts());
        status.put("dispatchRecoveryDispatchTimeout", taskCallbackProperties.getRecovery().getDispatchTimeout().toString());
        status.put("taskDispatchRecoveryEnabled", taskDispatchRecoveryProperties.isEnabled());
        status.put("taskDispatchRecoveryScannerEnabled", taskDispatchRecoveryProperties.isScannerEnabled());
        status.put("taskDispatchRecoveryMaxBatchSize", taskDispatchRecoveryProperties.getMaxBatchSize());
        status.put("taskDispatchRecoveryInitialDelay", taskDispatchRecoveryProperties.getInitialDelay().toString());
        status.put("taskDispatchRecoveryMaxDelay", taskDispatchRecoveryProperties.getMaxDelay().toString());
        status.put("callbackErrorContractEnabled", true);
        status.put("adapterActionStore", adapterActions.storeMode());
        status.put("adapterExecutorAuditStore", adapterActions.executorAuditStoreMode());
        status.put("adapterWorkerRetryEnabled", adapterActionProperties.getWorker().isRetryEnabled());
        status.put("adapterWorkerMaxAttempts", adapterActionProperties.getWorker().getMaxAttempts());
        status.put("adapterWorkerExpiredLeaseScanBatchSize", adapterActionProperties.getWorker().getExpiredLeaseScanBatchSize());
        status.put("adapterExecutorMode", adapterActionExecutionProperties.getMode());
        status.put("adapterExecutorEmbeddedMode", adapterActionExecutionProperties.isEmbeddedMode());
        status.put("adapterExecutorExternalMode", adapterActionExecutionProperties.isExternalMode());
        status.put("integrationEventStore", integrationEvents.storeMode());
        status.put("integrationEventProjectionEnabled", integrationEventProperties.isProjectionEnabled());
        status.put("integrationEventDeliveryEnabled", integrationEventProperties.isDeliveryEnabled());
        status.put("integrationEventSink", integrationEventProperties.getSink());
        status.put("fingerprintEnabled", fingerprintPolicyProperties.isEnabled());
        status.put("fingerprintPolicyVersion", fingerprintPolicyProperties.getPolicyVersion());
        status.put("fingerprintDefaultFields", fingerprintPolicyProperties.getDefaultFields());
        status.put("fingerprintPolicyCount", fingerprintPolicyProperties.getPolicies() == null ? 0 : fingerprintPolicyProperties.getPolicies().size());
        status.put("fingerprintMessageMaskingEnabled", fingerprintPolicyProperties.getMasking() != null && fingerprintPolicyProperties.getMasking().isEnabled());
        status.put("incidentAutoResolveEnabled", lifecycleProperties.getIncident().isAutoResolveEnabled());
        status.put("incidentInactiveThreshold", lifecycleProperties.getIncident().getInactiveThreshold().toString());
        status.put("incidentReopenPolicy", lifecycleProperties.getIncident().getReopenPolicy().name());
        status.put("incidentReopenWindow", lifecycleProperties.getIncident().getReopenWindow().toString());
        status.put("taskTimeoutEnabled", lifecycleProperties.getTask().isTimeoutEnabled());
        status.put("taskAutoReassignEnabled", lifecycleProperties.getTask().isAutoReassignEnabled());
        status.put("taskCreatedTimeout", lifecycleProperties.getTask().getCreatedTimeout().toString());
        status.put("taskAssignedTimeout", lifecycleProperties.getTask().getAssignedTimeout().toString());
        status.put("taskDispatchedTimeout", lifecycleProperties.getTask().getDispatchedTimeout().toString());
        status.put("taskRunningTimeout", lifecycleProperties.getTask().getRunningTimeout().toString());
        status.put("taskMaxReassignments", lifecycleProperties.getTask().getMaxReassignments());
        status.put("observabilityEnabled", observabilityProperties.isEnabled());
        status.put("businessMetricsEnabled", observabilityProperties.isBusinessMetricsEnabled());
        status.put("repositorySummaryEnabled", observabilityProperties.isRepositorySummaryEnabled());
        status.put("healthIndicatorEnabled", observabilityProperties.isHealthIndicatorEnabled());
        status.put("opsSummarySampleLimit", observabilityProperties.getSummarySampleLimit());
        status.put("taskCreationEnabled", taskProperties.isTaskCreationEnabled());
        status.put("taskEscalationEnabled", taskProperties.isTaskEscalationEnabled());
        status.put("taskMinOccurrences", taskProperties.getTaskMinOccurrences());
        status.put("immediateTaskSeverities", taskProperties.getImmediateTaskSeverities());
        status.put("mcpActionEnabled", properties.isMcpActionEnabled());
        status.put("issueActionEnabled", properties.isIssueActionEnabled());
        status.put("now", OffsetDateTime.now(ZoneOffset.UTC).toString());
        return status;
    }
}
