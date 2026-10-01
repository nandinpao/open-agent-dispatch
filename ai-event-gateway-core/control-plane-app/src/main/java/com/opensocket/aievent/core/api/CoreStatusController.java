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
import com.opensocket.aievent.core.action.AdapterActionMcpRuntimeConfigurationView;
import com.opensocket.aievent.core.action.AdapterActionPolicyRuntimeConfigurationView;
import com.opensocket.aievent.core.action.AdapterActionWorkerRuntimeConfigurationView;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterExecutorRuntimeConfigurationView;
import com.opensocket.aievent.core.agent.AgentControlOperationalQuery;
import com.opensocket.aievent.core.config.CoreDecisionProperties;
import com.opensocket.aievent.core.config.CoreDeploymentProperties;
import com.opensocket.aievent.core.callback.TaskCallbackProperties;
import com.opensocket.aievent.core.callback.TaskCallbackRuntimeConfigurationView;
import com.opensocket.aievent.core.dispatch.DispatchProperties;
import com.opensocket.aievent.core.dispatch.DispatchRuntimeConfigurationView;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.fingerprint.FingerprintRuntimeConfigurationView;
import com.opensocket.aievent.core.incident.IncidentOperationalQuery;
import com.opensocket.aievent.core.incident.IncidentRuntimeConfigurationView;
import com.opensocket.aievent.core.integration.IntegrationEventOperationalQuery;
import com.opensocket.aievent.core.integration.IntegrationEventProperties;
import com.opensocket.aievent.core.kernel.CoreVersion;
import com.opensocket.aievent.core.lifecycle.LifecycleProperties;
import com.opensocket.aievent.core.lifecycle.TaskLifecycleRuntimeConfigurationView;
import com.opensocket.aievent.core.observability.ObservabilityProperties;
import com.opensocket.aievent.core.observability.CoreObservabilityRuntimeConfigurationView;
import com.opensocket.aievent.core.observability.AgentRemediationMetricsRuntimeConfigurationView;
import com.opensocket.aievent.core.processing.EventProcessingOperationalQuery;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskDecisionRuntimeConfigurationView;
import com.opensocket.aievent.core.task.TaskDispatchRecoveryRuntimeConfigurationView;

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
    private final TaskDecisionRuntimeConfigurationView taskDecisionRuntimeConfiguration;
    private final TaskDispatchRecoveryRuntimeConfigurationView taskDispatchRecoveryRuntimeConfiguration;
    private final RoutingProperties routingProperties;
    private final DispatchProperties dispatchProperties;
    private final DispatchRuntimeConfigurationView dispatchRuntimeConfiguration;
    private final TaskCallbackProperties taskCallbackProperties;
    private final TaskCallbackRuntimeConfigurationView taskCallbackRuntimeConfiguration;
    private final AdapterActionWorkerRuntimeConfigurationView adapterActionWorkerRuntimeConfiguration;
    private final AdapterActionMcpRuntimeConfigurationView adapterActionMcpRuntimeConfiguration;
    private final AdapterActionPolicyRuntimeConfigurationView adapterActionPolicyRuntimeConfiguration;
    private final AdapterActionExecutionProperties adapterActionExecutionProperties;
    private final AdapterExecutorRuntimeConfigurationView adapterExecutorRuntimeConfiguration;
    private final FingerprintRuntimeConfigurationView fingerprintRuntimeConfiguration;
    private final LifecycleProperties lifecycleProperties;
    private final TaskLifecycleRuntimeConfigurationView taskLifecycleRuntimeConfiguration;
    private final IncidentRuntimeConfigurationView incidentRuntimeConfiguration;
    private final ObservabilityProperties observabilityProperties;
    private final CoreObservabilityRuntimeConfigurationView observabilityRuntimeConfiguration;
    private final AgentRemediationMetricsRuntimeConfigurationView remediationMetricsRuntimeConfiguration;

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
                                TaskDecisionRuntimeConfigurationView taskDecisionRuntimeConfiguration,
                                TaskDispatchRecoveryRuntimeConfigurationView taskDispatchRecoveryRuntimeConfiguration,
                                RoutingProperties routingProperties,
                                DispatchProperties dispatchProperties,
                                DispatchRuntimeConfigurationView dispatchRuntimeConfiguration,
                                TaskCallbackProperties taskCallbackProperties,
                                TaskCallbackRuntimeConfigurationView taskCallbackRuntimeConfiguration,
                                AdapterActionWorkerRuntimeConfigurationView adapterActionWorkerRuntimeConfiguration,
                                AdapterActionMcpRuntimeConfigurationView adapterActionMcpRuntimeConfiguration,
                                AdapterActionPolicyRuntimeConfigurationView adapterActionPolicyRuntimeConfiguration,
                                AdapterActionExecutionProperties adapterActionExecutionProperties,
                                AdapterExecutorRuntimeConfigurationView adapterExecutorRuntimeConfiguration,
                                FingerprintRuntimeConfigurationView fingerprintRuntimeConfiguration,
                                LifecycleProperties lifecycleProperties,
                                TaskLifecycleRuntimeConfigurationView taskLifecycleRuntimeConfiguration,
                                IncidentRuntimeConfigurationView incidentRuntimeConfiguration,
                                ObservabilityProperties observabilityProperties,
                                CoreObservabilityRuntimeConfigurationView observabilityRuntimeConfiguration,
                                AgentRemediationMetricsRuntimeConfigurationView remediationMetricsRuntimeConfiguration) {
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
        this.taskDecisionRuntimeConfiguration = taskDecisionRuntimeConfiguration;
        this.taskDispatchRecoveryRuntimeConfiguration = taskDispatchRecoveryRuntimeConfiguration;
        this.routingProperties = routingProperties;
        this.dispatchProperties = dispatchProperties;
        this.dispatchRuntimeConfiguration = dispatchRuntimeConfiguration;
        this.taskCallbackProperties = taskCallbackProperties;
        this.taskCallbackRuntimeConfiguration = taskCallbackRuntimeConfiguration;
        this.adapterActionWorkerRuntimeConfiguration = adapterActionWorkerRuntimeConfiguration;
        this.adapterActionMcpRuntimeConfiguration = adapterActionMcpRuntimeConfiguration;
        this.adapterActionPolicyRuntimeConfiguration = adapterActionPolicyRuntimeConfiguration;
        this.adapterActionExecutionProperties = adapterActionExecutionProperties;
        this.adapterExecutorRuntimeConfiguration = adapterExecutorRuntimeConfiguration;
        this.fingerprintRuntimeConfiguration = fingerprintRuntimeConfiguration;
        this.lifecycleProperties = lifecycleProperties;
        this.taskLifecycleRuntimeConfiguration = taskLifecycleRuntimeConfiguration;
        this.incidentRuntimeConfiguration = incidentRuntimeConfiguration;
        this.observabilityProperties = observabilityProperties;
        this.observabilityRuntimeConfiguration = observabilityRuntimeConfiguration;
        this.remediationMetricsRuntimeConfiguration = remediationMetricsRuntimeConfiguration;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("app", "ai-event-gateway-core");
        status.put("version", CoreVersion.CURRENT);
        status.put("releaseLabel", CoreVersion.RELEASE_LABEL);
        status.put("repositorySnapshot", CoreVersion.REPOSITORY_SNAPSHOT);
        status.put("artifactRevision", CoreVersion.ARTIFACT_REVISION);
        status.put("artifactName", CoreVersion.ARTIFACT_NAME);
        status.put("releaseGateStatus", CoreVersion.RELEASE_GATE_STATUS);
        status.put("runtimeCertificationStatus", CoreVersion.RUNTIME_CERTIFICATION_STATUS);
        status.put("certificationClaim", CoreVersion.CERTIFICATION_CLAIM);
        status.put("identityGeneratedAt", CoreVersion.IDENTITY_GENERATED_AT);
        status.put("productionReady", CoreVersion.PRODUCTION_READY);
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
        status.put("agentRemediationWorkflowMetricsEnabled", remediationMetricsRuntimeConfiguration == null ? observabilityProperties.getRemediationWorkflowMetrics().isEnabled() : remediationMetricsRuntimeConfiguration.enabled());
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
        status.put("dispatchReviewMode", dispatchRuntimeConfiguration.reviewMode());
        status.put("dispatchRequireAssignableAgent", dispatchRuntimeConfiguration.requireAssignableAgent());
        status.put("dispatchClientEnabled", dispatchProperties.getClient().isEnabled());
        status.put("dispatchClientAutoExecuteApproved", dispatchProperties.getClient().isAutoExecuteApproved());
        status.put("dispatchClientDefaultGatewayBaseUrl", dispatchRuntimeConfiguration.defaultGatewayBaseUrl());
        status.put("dispatchExecutionPolicy", dispatchRuntimeConfiguration.executionPolicy());
        status.put("dispatchSourceNodeId", dispatchRuntimeConfiguration.sourceNodeId());
        status.put("dispatchWorkerId", dispatchRuntimeConfiguration.workerId());
        status.put("dispatchGatewayDispatchPath", dispatchRuntimeConfiguration.gatewayDispatchPath());
        status.put("dispatchGatewayBaseUrls", dispatchRuntimeConfiguration.gatewayBaseUrls());
        status.put("dispatchClientConnectTimeout", dispatchRuntimeConfiguration.connectTimeout().toString());
        status.put("dispatchClientRequestTimeout", dispatchRuntimeConfiguration.requestTimeout().toString());
        status.put("dispatchClientMaxBatchSize", dispatchRuntimeConfiguration.maxBatchSize());
        status.put("dispatchAutoExecuteIntervalMs", dispatchRuntimeConfiguration.autoExecuteInterval().toMillis());
        status.put("dispatchClaimLease", dispatchRuntimeConfiguration.claimLease().toString());
        status.put("taskCallbackStore", execution.callbackStoreMode());
        status.put("taskCallbackIdempotencyEnabled", taskCallbackRuntimeConfiguration.idempotencyEnabled());
        status.put("taskCallbackRequireDispatchToken", taskCallbackProperties.isRequireDispatchToken());
        status.put("taskCallbackEnforceStateTransition", taskCallbackRuntimeConfiguration.enforceStateTransition());
        status.put("taskCallbackRejectOldAttemptCallbacks", taskCallbackRuntimeConfiguration.rejectOldAttemptCallbacks());
        status.put("taskCallbackRequireAttemptNo", taskCallbackRuntimeConfiguration.requireAttemptNo());
        status.put("taskCallbackEnforceGatewayAndAgentIdentity", taskCallbackRuntimeConfiguration.enforceGatewayAndAgentIdentity());
        status.put("dispatchRecoveryTimeoutEnabled", taskCallbackRuntimeConfiguration.recoveryTimeoutEnabled());
        status.put("dispatchRecoveryRetryEnabled", taskCallbackRuntimeConfiguration.recoveryRetryEnabled());
        status.put("dispatchRetryEnabled", dispatchRuntimeConfiguration.retryEnabled());
        status.put("dispatchRetryMaxAttempts", dispatchRuntimeConfiguration.maxAttempts());
        status.put("dispatchRuntimeConfigBacked", dispatchRuntimeConfiguration.runtimeBacked());
        status.put("dispatchRuntimeConfigRevision", dispatchRuntimeConfiguration.revisionId());
        status.put("dispatchRecoveryDispatchTimeout", taskCallbackRuntimeConfiguration.recoveryDispatchTimeout().toString());
        status.put("taskDispatchRecoveryEnabled", taskDispatchRecoveryRuntimeConfiguration.enabled());
        status.put("taskDispatchRecoveryScannerEnabled", taskDispatchRecoveryRuntimeConfiguration.scannerEnabled());
        status.put("taskDispatchRecoveryMaxBatchSize", taskDispatchRecoveryRuntimeConfiguration.maxBatchSize());
        status.put("taskDispatchRecoveryInitialDelay", taskDispatchRecoveryRuntimeConfiguration.initialDelay().toString());
        status.put("taskDispatchRecoveryMaxDelay", taskDispatchRecoveryRuntimeConfiguration.maxDelay().toString());
        status.put("callbackErrorContractEnabled", true);
        status.put("adapterActionStore", adapterActions.storeMode());
        status.put("adapterExecutorAuditStore", adapterActions.executorAuditStoreMode());
        status.put("adapterWorkerRetryEnabled", adapterActionWorkerRuntimeConfiguration.retryEnabled());
        status.put("adapterWorkerMaxAttempts", adapterActionWorkerRuntimeConfiguration.maxAttempts());
        status.put("adapterWorkerExpiredLeaseScanBatchSize", adapterActionWorkerRuntimeConfiguration.expiredLeaseScanBatchSize());
        status.put("adapterWorkerExpiredLeaseScanIntervalMs", adapterActionWorkerRuntimeConfiguration.expiredLeaseScanInterval().toMillis());
        status.put("adapterWorkerRuntimeConfigBacked", adapterActionWorkerRuntimeConfiguration.runtimeBacked());
        status.put("adapterWorkerRuntimeConfigRevision", adapterActionWorkerRuntimeConfiguration.revisionId());
        status.put("adapterMcpEnabled", adapterActionMcpRuntimeConfiguration.enabled());
        status.put("adapterMcpAdapterName", adapterActionMcpRuntimeConfiguration.adapterName());
        status.put("adapterActionCreateSuppressedRecords", adapterActionPolicyRuntimeConfiguration.createSuppressedRecords());
        status.put("adapterIssueAdapterName", adapterActionPolicyRuntimeConfiguration.issueAdapterName());
        status.put("adapterMcpRunOnCompletedTask", adapterActionMcpRuntimeConfiguration.runOnCompletedTask());
        status.put("adapterMcpRunOnFailedTask", adapterActionMcpRuntimeConfiguration.runOnFailedTask());
        status.put("adapterMcpOnePerTask", adapterActionMcpRuntimeConfiguration.onePerTask());
        status.put("adapterMcpRuntimeConfigBacked", adapterActionMcpRuntimeConfiguration.runtimeBacked());
        status.put("adapterMcpRuntimeConfigRevision", adapterActionMcpRuntimeConfiguration.revisionId());
        status.put("adapterExecutorMode", adapterActionExecutionProperties.getMode());
        status.put("adapterExecutorEmbeddedMode", adapterActionExecutionProperties.isEmbeddedMode());
        status.put("adapterExecutorExternalMode", adapterActionExecutionProperties.isExternalMode());
        status.put("adapterExecutorCircuitBreakerEnabled", adapterExecutorRuntimeConfiguration.circuitBreakerEnabled());
        status.put("adapterExecutorCircuitBreakerFailureThreshold", adapterExecutorRuntimeConfiguration.circuitBreakerFailureThreshold());
        status.put("adapterExecutorCircuitBreakerOpenDuration", adapterExecutorRuntimeConfiguration.circuitBreakerOpenDuration().toString());
        status.put("adapterExecutorCircuitBreakerRuntimeConfigBacked", adapterExecutorRuntimeConfiguration.circuitBreakerRuntimeBacked());
        status.put("adapterExecutorCircuitBreakerRuntimeConfigRevision", adapterExecutorRuntimeConfiguration.revisionId());
        status.put("issueExecutionAuthority", adapterActionExecutionProperties.getIssue().getExecutionAuthority().name());
        status.put("issueConnectorRuntimeEnabled", adapterExecutorRuntimeConfiguration.issueConnectorRuntimeEnabled());
        status.put("issueConnectorRuntimeRequired", adapterActionExecutionProperties.getIssue().isConnectorRuntimeRequired());
        status.put("issueAutoExecutePending", adapterExecutorRuntimeConfiguration.issueAutoExecutePending());
        status.put("issueLinkProjectionReconciliationEnabled", adapterExecutorRuntimeConfiguration.issueLinkProjectionReconciliationEnabled());
        status.put("issueLinkProjectionMaxAttempts", adapterExecutorRuntimeConfiguration.issueLinkProjectionMaxAttempts());
        status.put("adapterExecutorAutoExecuteInterval", adapterExecutorRuntimeConfiguration.autoExecuteInterval().toString());
        status.put("adapterExecutorAuditPayloadSnapshotEnabled", adapterExecutorRuntimeConfiguration.auditPayloadSnapshotEnabled());
        status.put("adapterExecutorMarkUnavailableWhenNoExecutor", adapterExecutorRuntimeConfiguration.markUnavailableWhenNoExecutor());
        status.put("adapterExecutorMcpHttpEnabled", adapterExecutorRuntimeConfiguration.mcpHttpEnabled());
        status.put("adapterExecutorMcpEndpointConfigured", !adapterExecutorRuntimeConfiguration.mcpEndpointUrl().isBlank());
        status.put("adapterExecutorMcpExecutorName", adapterExecutorRuntimeConfiguration.mcpExecutorName());
        status.put("adapterExecutorMcpTimeout", adapterExecutorRuntimeConfiguration.mcpTimeout().toString());
        status.put("issueDefaultVendor", adapterExecutorRuntimeConfiguration.issueDefaultVendor());
        status.put("issueLinkProjectionBatchSize", adapterExecutorRuntimeConfiguration.issueLinkProjectionBatchSize());
        status.put("issueExternalWorkerAllowed", false);
        status.put("integrationEventStore", integrationEvents.storeMode());
        status.put("integrationEventProjectionEnabled", integrationEventProperties.isProjectionEnabled());
        status.put("integrationEventDeliveryEnabled", integrationEventProperties.isDeliveryEnabled());
        status.put("integrationEventSink", integrationEventProperties.getSink());
        status.put("fingerprintEnabled", fingerprintRuntimeConfiguration.enabled());
        status.put("fingerprintPolicyVersion", fingerprintRuntimeConfiguration.policyVersion());
        status.put("fingerprintDefaultFields", fingerprintRuntimeConfiguration.defaultFields());
        status.put("fingerprintPolicyCount", fingerprintRuntimeConfiguration.policies().size());
        status.put("fingerprintMessageMaskingEnabled", fingerprintRuntimeConfiguration.maskingEnabled());
        status.put("fingerprintRuntimeConfigBacked", fingerprintRuntimeConfiguration.runtimeBacked());
        status.put("fingerprintRuntimeConfigVersion", fingerprintRuntimeConfiguration.snapshotVersionToken());
        status.put("incidentAutoResolveEnabled", lifecycleProperties.getIncident().isAutoResolveEnabled());
        status.put("incidentInactiveThreshold", incidentRuntimeConfiguration.inactiveThreshold().toString());
        status.put("incidentReopenPolicy", incidentRuntimeConfiguration.reopenPolicy().name());
        status.put("incidentReopenWindow", incidentRuntimeConfiguration.reopenWindow().toString());
        status.put("incidentRuntimeConfigBacked", incidentRuntimeConfiguration.runtimeBacked());
        status.put("incidentRuntimeConfigRevision", incidentRuntimeConfiguration.revisionId());
        status.put("taskTimeoutEnabled", taskLifecycleRuntimeConfiguration.timeoutEnabled());
        status.put("taskAutoReassignEnabled", taskLifecycleRuntimeConfiguration.autoReassignEnabled());
        status.put("taskCreatedTimeout", taskLifecycleRuntimeConfiguration.createdTimeout().toString());
        status.put("taskAssignedTimeout", taskLifecycleRuntimeConfiguration.assignedTimeout().toString());
        status.put("taskDispatchedTimeout", taskLifecycleRuntimeConfiguration.dispatchedTimeout().toString());
        status.put("taskRunningTimeout", taskLifecycleRuntimeConfiguration.runningTimeout().toString());
        status.put("taskMaxReassignments", taskLifecycleRuntimeConfiguration.maxReassignments());
        status.put("taskRuntimeConfigBacked", taskLifecycleRuntimeConfiguration.runtimeBacked());
        status.put("taskRuntimeConfigRevision", taskLifecycleRuntimeConfiguration.revisionId());
        status.put("observabilityEnabled", observabilityRuntimeConfiguration.enabled());
        status.put("businessMetricsEnabled", observabilityRuntimeConfiguration.businessMetricsEnabled());
        status.put("repositorySummaryEnabled", observabilityProperties.isRepositorySummaryEnabled());
        status.put("healthIndicatorEnabled", observabilityRuntimeConfiguration.healthIndicatorEnabled());
        status.put("opsSummarySampleLimit", observabilityRuntimeConfiguration.summarySampleLimit());
        status.put("observabilityRuntimeConfigBacked", observabilityRuntimeConfiguration.runtimeBacked());
        status.put("observabilityRuntimeConfigRevision", observabilityRuntimeConfiguration.revisionId());
        status.put("taskCreationEnabled", taskDecisionRuntimeConfiguration.taskCreationEnabled());
        status.put("taskEscalationEnabled", taskDecisionRuntimeConfiguration.taskEscalationEnabled());
        status.put("taskMinOccurrences", taskDecisionRuntimeConfiguration.taskMinOccurrences());
        status.put("immediateTaskSeverities", taskDecisionRuntimeConfiguration.immediateTaskSeverities());
        status.put("taskDefaultRoutingPolicy", taskDecisionRuntimeConfiguration.defaultRoutingPolicy());
        status.put("mcpActionEnabled", properties.isMcpActionEnabled());
        status.put("issueActionEnabled", properties.isIssueActionEnabled());
        status.put("now", OffsetDateTime.now(ZoneOffset.UTC).toString());
        return status;
    }
}
