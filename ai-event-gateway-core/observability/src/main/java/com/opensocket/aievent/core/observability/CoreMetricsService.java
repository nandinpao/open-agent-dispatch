package com.opensocket.aievent.core.observability;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionMetricsPort;
import com.opensocket.aievent.core.callback.TaskCallbackResult;
import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.decision.DecisionType;
import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryRecord;
import com.opensocket.aievent.core.dispatch.DispatchExecutionResult;
import com.opensocket.aievent.core.dispatch.DispatchRecoveryMetricsPort;
import com.opensocket.aievent.core.dispatch.ExecutionMetricsPort;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.lifecycle.LifecycleScanResult;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.routing.RoutingMetricsPort;
import com.opensocket.aievent.core.task.TaskDecisionResult;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class CoreMetricsService implements RoutingMetricsPort, ExecutionMetricsPort, AdapterActionMetricsPort, DispatchRecoveryMetricsPort {
    private final MeterRegistry meterRegistry;
    private final ObservabilityProperties properties;

    public CoreMetricsService(MeterRegistry meterRegistry, ObservabilityProperties properties) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    public void recordIntake(NormalizedEvent event,
                             DedupDecision dedup,
                             Incident incident,
                             DecisionType decisionType,
                             TaskDecisionResult taskDecision,
                             Duration elapsed) {
        if (!enabled()) return;
        String sourceSystem = tag(event == null ? null : event.sourceSystem());
        String severity = tag(event == null || event.severity() == null ? null : event.severity().name());
        String duplicate = dedup == null ? "unknown" : Boolean.toString(dedup.duplicate());
        String incidentStatus = tag(incident == null || incident.getStatus() == null ? null : incident.getStatus().name());
        String decision = tag(decisionType == null ? null : decisionType.name());
        String taskOutcome = taskOutcome(taskDecision);

        counter("aeg.core.events.intake.total",
                tags("source_system", sourceSystem, "severity", severity, "duplicate", duplicate, "decision", decision, "task_outcome", taskOutcome)).increment();
        counter("aeg.core.dedup.decisions.total",
                tags("source_system", sourceSystem, "duplicate", duplicate)).increment();
        counter("aeg.core.incidents.decisions.total",
                tags("status", incidentStatus, "decision", decision, "severity", severity)).increment();
        if (taskDecision != null) {
            counter("aeg.core.tasks.decisions.total",
                    tags("outcome", taskOutcome, "task_type", tag(taskDecision.taskType() == null ? null : taskDecision.taskType().name()),
                            "assignment", bool(taskDecision.assignmentCreated()), "dispatch", bool(taskDecision.dispatchRequestCreated()))).increment();
        }
        if (elapsed != null) {
            timer("aeg.core.events.intake.duration", tags("source_system", sourceSystem, "severity", severity)).record(elapsed);
            if (elapsed.compareTo(properties.getSlowIntakeThreshold()) > 0) {
                counter("aeg.core.events.intake.slow.total", tags("source_system", sourceSystem, "severity", severity)).increment();
            }
        }
    }

    @Override
    public void recordRoutingDecision(RoutingDecisionRecord decision) {
        if (!enabled() || decision == null) return;
        counter("aeg.core.routing.decisions.total",
                tags("policy", tag(decision.getRoutingPolicy() == null ? null : decision.getRoutingPolicy().name()),
                        "status", tag(decision.getStatus() == null ? null : decision.getStatus().name()),
                        "selected", bool(decision.getSelectedAgentId() != null && !decision.getSelectedAgentId().isBlank()))).increment();
    }

    @Override
    public void recordDispatchExecution(DispatchExecutionResult result, Duration elapsed) {
        if (!enabled() || result == null) return;
        String status = tag(result.getDispatchStatus());
        String executed = bool(result.isExecuted());
        String gatewayStatus = gatewayStatusTag(result.getGatewayStatus());
        counter("aeg.core.dispatch.executions.total",
                tags("executed", executed, "dispatch_status", status, "gateway_status", gatewayStatus,
                        "task_status", tag(result.getTaskStatus()))).increment();
        if (elapsed != null) {
            timer("aeg.core.dispatch.execution.duration", tags("executed", executed, "dispatch_status", status)).record(elapsed);
        }
    }

    @Override
    public void recordCallback(TaskCallbackResult result) {
        if (!enabled() || result == null) return;
        counter("aeg.core.callbacks.total",
                tags("callback_type", tag(result.getCallbackType()),
                        "accepted", bool(result.isAccepted()),
                        "duplicate", bool(result.isDuplicate()),
                        "error_code", tag(result.getErrorCode()),
                        "retryable", bool(result.isRetryable()),
                        "task_status", tag(result.getTaskStatus()),
                        "dispatch_status", tag(result.getDispatchStatus()))).increment();
    }

    @Override
    public void recordAdapterAction(AdapterAction action, String operation) {
        if (!enabled() || action == null) return;
        counter("aeg.core.adapter_actions.total",
                tags("operation", tag(operation),
                        "adapter_type", tag(action.getAdapterType() == null ? null : action.getAdapterType().name()),
                        "action_type", tag(action.getActionType() == null ? null : action.getActionType().name()),
                        "status", tag(action.getStatus() == null ? null : action.getStatus().name()))).increment();
    }


    @Override
    public void recordDispatchRecoveryEvent(DispatchAttemptHistoryRecord record) {
        if (!enabled() || record == null) return;
        counter("aeg.core.dispatch.recovery.events.total",
                tags("event_type", tag(record.getEventType()),
                        "status", tag(record.getStatus()),
                        "error_code", tag(record.getErrorCode()),
                        "site_id", tag(properties.isIncludeSiteTag() ? record.getSiteId() : null))).increment();
    }

    public void recordLifecycleScan(String target, LifecycleScanResult result) {
        if (!enabled() || result == null) return;
        String safeTarget = tag(target);
        counter("aeg.core.lifecycle.scans.total", tags("target", safeTarget)).increment();
        if (result.getUpdated() > 0) {
            counter("aeg.core.lifecycle.updated.total", tags("target", safeTarget)).increment(result.getUpdated());
        }
        if (result.getTimedOut() > 0) {
            counter("aeg.core.lifecycle.tasks.timed_out.total", tags("target", safeTarget)).increment(result.getTimedOut());
        }
        if (result.getReassigned() > 0) {
            counter("aeg.core.lifecycle.tasks.reassigned.total", tags("target", safeTarget)).increment(result.getReassigned());
        }
    }

    private boolean enabled() {
        return properties.isEnabled() && properties.isBusinessMetricsEnabled();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name)
                .description(description(name))
                .tags(withCommonTags(tags))
                .register(meterRegistry);
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name)
                .description(description(name))
                .tags(withCommonTags(tags))
                .register(meterRegistry);
    }

    private String[] tags(String... tags) {
        return tags == null ? new String[0] : tags;
    }

    private String[] withCommonTags(String... tags) {
        List<String> merged = new ArrayList<>();
        properties.getCommonTags().forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                merged.add(key);
                merged.add(tag(value));
            }
        });
        if (tags != null) {
            for (int i = 0; i + 1 < tags.length; i += 2) {
                merged.add(tagKey(tags[i]));
                merged.add(tag(tags[i + 1]));
            }
        }
        return merged.toArray(String[]::new);
    }

    private String taskOutcome(TaskDecisionResult result) {
        if (result == null) return "not_evaluated";
        if (result.taskCreated()) return "created";
        if (result.taskSuppressed()) return "suppressed";
        return "none";
    }

    private String bool(boolean value) { return Boolean.toString(value); }

    private String tag(Object value) {
        if (value == null) return "none";
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return "none";
        String normalized = text.toLowerCase().replace(' ', '_');
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private String gatewayStatusTag(String value) {
        if (value == null || value.isBlank()) return "none";
        String normalized = value.trim().toUpperCase();
        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.length() >= 3) {
            try {
                int code = Integer.parseInt(digits.substring(0, 3));
                if (code >= 100 && code <= 599) {
                    return "http_" + (code / 100) + "xx";
                }
            } catch (NumberFormatException ignored) {
                // Fall back to the generic low-cardinality buckets below.
            }
        }
        if (normalized.contains("TIMEOUT")) return "timeout";
        if (normalized.contains("NOT_WRITABLE")) return "not_writable";
        if (normalized.contains("NOT_CONNECTED")) return "not_connected";
        if (normalized.contains("UNAUTHORIZED") || normalized.contains("FORBIDDEN")) return "unauthorized";
        return tag(value);
    }

    private String tagKey(String value) {
        return value == null || value.isBlank() ? "tag" : value.trim().toLowerCase().replace(' ', '_');
    }

    private String description(String name) {
        return switch (name) {
            case "aeg.core.events.intake.total" -> "Total event intake decisions processed by ai-event-gateway-core.";
            case "aeg.core.dedup.decisions.total" -> "Total dedup decisions by source system and duplicate result.";
            case "aeg.core.incidents.decisions.total" -> "Total incident decision outcomes.";
            case "aeg.core.tasks.decisions.total" -> "Total task decision outcomes.";
            case "aeg.core.routing.decisions.total" -> "Total routing decisions and selected-agent outcomes.";
            case "aeg.core.dispatch.executions.total" -> "Total dispatch execution attempts.";
            case "aeg.core.callbacks.total" -> "Total task callback results by error contract.";
            case "aeg.core.dispatch.recovery.events.total" -> "Total dispatch recovery timeline events by event type and status.";
            case "aeg.core.adapter_actions.total" -> "Total adapter action operations.";
            default -> name;
        };
    }
}
