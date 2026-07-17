package com.opensocket.aievent.core.observability;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionFacade;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.agent.AgentControlOperationalQuery;
import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.incident.IncidentOperationalQuery;
import com.opensocket.aievent.core.integration.IntegrationEventOperationalQuery;
import com.opensocket.aievent.core.outbox.OutboxOperationalQuery;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.routing.RoutingDecisionStatus;
import com.opensocket.aievent.core.task.TaskOperationalQuery;

@Service
public class OperationalSummaryService {
    private final IncidentOperationalQuery incidentQuery;
    private final TaskOperationalQuery taskQuery;
    private final ExecutionOperationalQuery executionQuery;
    private final AdapterActionFacade adapterActionQuery;
    private final AgentControlOperationalQuery agentQuery;
    private final OutboxOperationalQuery outboxQuery;
    private final IntegrationEventOperationalQuery integrationEventQuery;
    private final ObservabilityProperties properties;

    public OperationalSummaryService(IncidentOperationalQuery incidentQuery,
                                     TaskOperationalQuery taskQuery,
                                     ExecutionOperationalQuery executionQuery,
                                     AdapterActionFacade adapterActionQuery,
                                     AgentControlOperationalQuery agentQuery,
                                     OutboxOperationalQuery outboxQuery,
                                     IntegrationEventOperationalQuery integrationEventQuery,
                                     ObservabilityProperties properties) {
        this.incidentQuery = incidentQuery;
        this.taskQuery = taskQuery;
        this.executionQuery = executionQuery;
        this.adapterActionQuery = adapterActionQuery;
        this.agentQuery = agentQuery;
        this.outboxQuery = outboxQuery;
        this.integrationEventQuery = integrationEventQuery;
        this.properties = properties;
    }

    public OperationalSummary summary() {
        OperationalSummary summary = new OperationalSummary();
        int limit = properties.getSummarySampleLimit();
        summary.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));
        summary.setSampleLimit(limit);
        summary.setStores(stores());
        if (!properties.isEnabled() || !properties.isRepositorySummaryEnabled()) {
            Map<String, Object> risks = new LinkedHashMap<>();
            risks.put("requiresAttention", false);
            risks.put("disabled", true);
            risks.put("reason", "repository summary disabled by core.observability.repository-summary-enabled=false");
            summary.setRiskSignals(risks);
            summary.setSloMetrics(disabledSloMetrics("repository summary disabled by core.observability.repository-summary-enabled=false"));
            return summary;
        }
        summary.setIncidents(incidentQuery.statusCounts(limit));
        summary.setTasks(taskQuery.taskStatusCounts(limit));
        summary.setDispatchRequests(executionQuery.dispatchStatusCounts(limit));
        summary.setAdapterActions(adapterActionQuery.statusCounts(limit));
        summary.setModuleEvents(outboxQuery.statusCounts(limit));
        summary.setIntegrationEvents(integrationEventQuery.statusCounts(limit));
        summary.setAgents(agentQuery.agentStatusCounts(limit));
        summary.setGatewayNodes(agentQuery.gatewayStatusCounts(limit));
        summary.setRiskSignals(riskSignals(summary));
        summary.setSloMetrics(sloMetrics(summary, limit));
        return summary;
    }

    public Map<String, Object> stores() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("incident", incidentQuery.incidentStoreMode());
        map.put("incidentSummary", incidentQuery.occurrenceSummaryStoreMode());
        map.put("task", taskQuery.taskStoreMode());
        map.put("assignment", taskQuery.assignmentStoreMode());
        map.put("routingDecision", taskQuery.routingStoreMode());
        map.put("dispatchRequest", executionQuery.dispatchStoreMode());
        map.put("taskCallback", executionQuery.callbackStoreMode());
        map.put("adapterAction", adapterActionQuery.storeMode());
        map.put("adapterExecutorAudit", adapterActionQuery.executorAuditStoreMode());
        map.put("moduleOutbox", outboxQuery.storeMode());
        map.put("integrationEventOutbox", integrationEventQuery.storeMode());
        map.put("agentDirectory", agentQuery.agentStoreMode());
        map.put("gatewayNode", agentQuery.gatewayStoreMode());
        return map;
    }

    private Map<String, Object> riskSignals(OperationalSummary summary) {
        Map<String, Object> map = new LinkedHashMap<>();
        int openTasks = sum(summary.getTasks(), List.of("CREATED", "ASSIGNED", "DISPATCHED", "RUNNING"));
        int problematicDispatch = sum(summary.getDispatchRequests(), List.of("RETRY_WAITING", "DEAD_LETTER", "FAILED", "TIMED_OUT"));
        int problematicActions = sum(summary.getAdapterActions(), List.of("RETRY_WAITING", "EXECUTOR_UNAVAILABLE", "FAILED"));
        int problematicOutbox = sum(summary.getModuleEvents(), List.of("RETRY_WAITING", "DEAD_LETTER"));
        int problematicIntegrationEvents = sum(summary.getIntegrationEvents(), List.of("RETRY_WAITING", "DEAD_LETTER"));
        int availableAgents = sum(summary.getAgents(), List.of("IDLE", "BUSY_ACCEPTING"));
        int onlineGateways = sum(summary.getGatewayNodes(), List.of("ONLINE", "DEGRADED"));
        map.put("openTasks", openTasks);
        map.put("problematicDispatchRequests", problematicDispatch);
        map.put("problematicAdapterActions", problematicActions);
        map.put("problematicModuleEvents", problematicOutbox);
        map.put("problematicIntegrationEvents", problematicIntegrationEvents);
        map.put("availableAgents", availableAgents);
        map.put("onlineGatewayNodes", onlineGateways);
        map.put("requiresAttention", problematicDispatch > 0 || problematicActions > 0 || problematicOutbox > 0 || problematicIntegrationEvents > 0 || (openTasks > 0 && availableAgents == 0));
        return map;
    }

    private Map<String, Object> sloMetrics(OperationalSummary summary, int limit) {
        var policy = properties.getSloMetrics();
        if (!policy.isEnabled()) {
            return disabledSloMetrics("core.observability.slo-metrics.enabled=false");
        }

        List<Map<String, Object>> alerts = new ArrayList<>();
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> callbackLag = callbackLagMetrics(limit);
        Map<String, Object> dispatchReliability = dispatchReliabilityMetrics(summary);
        Map<String, Object> adapterExecutor = adapterExecutorMetrics(limit, summary);
        Map<String, Object> routing = routingMetrics(limit);

        map.put("callbackLag", callbackLag);
        map.put("dispatchReliability", dispatchReliability);
        map.put("adapterExecutor", adapterExecutor);
        map.put("routingNoCandidate", routing);

        collectAlert(alerts, "callbackLag", (String) callbackLag.get("status"), "Max callback lag seconds=" + callbackLag.get("maxSeconds"));
        collectAlert(alerts, "dispatchReliability", (String) dispatchReliability.get("status"), "Retry waiting=" + dispatchReliability.get("retryWaiting") + ", dead-letter=" + dispatchReliability.get("deadLetter"));
        collectAlert(alerts, "adapterExecutor", (String) adapterExecutor.get("status"), "Failure ratio=" + adapterExecutor.get("failureRatio"));
        collectAlert(alerts, "routingNoCandidate", (String) routing.get("status"), "No-candidate ratio=" + routing.get("noCandidateRatio"));

        String status = alerts.stream().anyMatch(alert -> "CRITICAL".equals(alert.get("severity"))) ? "CRITICAL"
                : alerts.stream().anyMatch(alert -> "WARNING".equals(alert.get("severity"))) ? "WARNING" : "OK";
        map.put("status", status);
        map.put("alerts", alerts);
        map.put("sampleLimit", limit);
        map.put("generatedAt", OffsetDateTime.now(ZoneOffset.UTC));
        return map;
    }

    private Map<String, Object> disabledSloMetrics(String reason) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "DISABLED");
        map.put("disabled", true);
        map.put("reason", reason);
        map.put("generatedAt", OffsetDateTime.now(ZoneOffset.UTC));
        return map;
    }

    private Map<String, Object> callbackLagMetrics(int limit) {
        var policy = properties.getSloMetrics();
        List<Long> lags = safeList(executionQuery.recentCallbacks(limit)).stream()
                .filter(callback -> callback.getOccurredAt() != null && callback.getProcessedAt() != null)
                .map(callback -> Math.max(0L, Duration.between(callback.getOccurredAt(), callback.getProcessedAt()).toSeconds()))
                .sorted()
                .toList();
        long max = lags.isEmpty() ? 0L : lags.get(lags.size() - 1);
        long p95 = percentile(lags, 0.95d);
        long warning = Math.max(0L, policy.getCallbackLagWarning().toSeconds());
        long critical = Math.max(warning, policy.getCallbackLagCritical().toSeconds());
        String status = max >= critical && critical > 0 ? "CRITICAL" : max >= warning && warning > 0 ? "WARNING" : "OK";
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status);
        map.put("sampleSize", lags.size());
        map.put("maxSeconds", max);
        map.put("p95Seconds", p95);
        map.put("warningThresholdSeconds", warning);
        map.put("criticalThresholdSeconds", critical);
        return map;
    }

    private Map<String, Object> dispatchReliabilityMetrics(OperationalSummary summary) {
        var policy = properties.getSloMetrics();
        int retryWaiting = summary.getDispatchRequests().getOrDefault("RETRY_WAITING", 0);
        int deadLetter = summary.getDispatchRequests().getOrDefault("DEAD_LETTER", 0);
        int failedOrTimedOut = sum(summary.getDispatchRequests(), List.of("FAILED", "TIMED_OUT"));
        int openOrTerminal = summary.getDispatchRequests().values().stream().mapToInt(Integer::intValue).sum();
        int problematic = retryWaiting + deadLetter + failedOrTimedOut;
        String status = deadLetter >= policy.getDispatchDeadLetterCriticalThreshold()
                || retryWaiting >= policy.getDispatchRetryCriticalThreshold() ? "CRITICAL"
                : deadLetter >= policy.getDispatchDeadLetterWarningThreshold()
                || retryWaiting >= policy.getDispatchRetryWarningThreshold() ? "WARNING" : "OK";
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status);
        map.put("retryWaiting", retryWaiting);
        map.put("deadLetter", deadLetter);
        map.put("failedOrTimedOut", failedOrTimedOut);
        map.put("problematic", problematic);
        map.put("sampleSize", openOrTerminal);
        map.put("problemRatio", ratio(problematic, openOrTerminal));
        return map;
    }

    private Map<String, Object> adapterExecutorMetrics(int limit, OperationalSummary summary) {
        var policy = properties.getSloMetrics();
        List<AdapterAction> recentActions = safeList(adapterActionQuery.recent(limit));
        int sampleSize = recentActions.isEmpty()
                ? summary.getAdapterActions().values().stream().mapToInt(Integer::intValue).sum()
                : recentActions.size();
        int failed = recentActions.isEmpty()
                ? sum(summary.getAdapterActions(), List.of("FAILED", "RETRY_WAITING", "EXECUTOR_UNAVAILABLE"))
                : (int) recentActions.stream().filter(action -> isProblematicAdapterStatus(action.getStatus())).count();
        double ratio = ratio(failed, sampleSize);
        String status = ratio >= policy.getAdapterFailureRatioCritical() && sampleSize > 0 ? "CRITICAL"
                : ratio >= policy.getAdapterFailureRatioWarning() && sampleSize > 0 ? "WARNING" : "OK";
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status);
        map.put("sampleSize", sampleSize);
        map.put("failedOrRetrying", failed);
        map.put("failureRatio", ratio);
        map.put("warningRatio", policy.getAdapterFailureRatioWarning());
        map.put("criticalRatio", policy.getAdapterFailureRatioCritical());
        return map;
    }

    private Map<String, Object> routingMetrics(int limit) {
        var policy = properties.getSloMetrics();
        List<RoutingDecisionRecord> decisions = safeList(taskQuery.recentRoutingDecisions(limit));
        int noCandidate = (int) decisions.stream().filter(decision -> decision.getStatus() == RoutingDecisionStatus.NO_CANDIDATE).count();
        int manualReview = (int) decisions.stream().filter(decision -> decision.getStatus() == RoutingDecisionStatus.MANUAL_REVIEW_REQUIRED).count();
        double noCandidateRatio = ratio(noCandidate, decisions.size());
        String status = noCandidateRatio >= policy.getRoutingNoCandidateRatioCritical() && !decisions.isEmpty() ? "CRITICAL"
                : noCandidateRatio >= policy.getRoutingNoCandidateRatioWarning() && !decisions.isEmpty() ? "WARNING" : "OK";
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status);
        map.put("sampleSize", decisions.size());
        map.put("noCandidate", noCandidate);
        map.put("manualReviewRequired", manualReview);
        map.put("noCandidateRatio", noCandidateRatio);
        map.put("warningRatio", policy.getRoutingNoCandidateRatioWarning());
        map.put("criticalRatio", policy.getRoutingNoCandidateRatioCritical());
        return map;
    }

    private boolean isProblematicAdapterStatus(AdapterActionStatus status) {
        return status == AdapterActionStatus.FAILED
                || status == AdapterActionStatus.RETRY_WAITING
                || status == AdapterActionStatus.EXECUTOR_UNAVAILABLE;
    }

    private void collectAlert(List<Map<String, Object>> alerts, String objective, String severity, String message) {
        if ("OK".equals(severity) || "DISABLED".equals(severity) || severity == null) return;
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("objective", objective);
        alert.put("severity", severity);
        alert.put("message", message);
        alerts.add(alert);
    }

    private long percentile(List<Long> values, double percentile) {
        if (values == null || values.isEmpty()) return 0L;
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private double ratio(int numerator, int denominator) {
        if (denominator <= 0) return 0.0d;
        return Math.round(((double) numerator / (double) denominator) * 10000.0d) / 10000.0d;
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private int sum(Map<String, Integer> map, List<String> keys) {
        int total = 0;
        for (String key : keys) total += map.getOrDefault(key, 0);
        return total;
    }
}
