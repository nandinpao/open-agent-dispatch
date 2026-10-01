package com.opensocket.aievent.core.lifecycle;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.observability.CoreMetricsService;
import com.opensocket.aievent.core.task.TaskLifecyclePolicy;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;
import com.opensocket.aievent.core.task.TaskRecord;

@Service
public class TaskLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(TaskLifecycleService.class);
    private final TaskOrchestrationFacade facade;
    private final LifecycleProperties properties;
    private final TaskLifecycleRuntimeConfigurationView runtimeConfiguration;

    @Autowired(required=false) private CoreMetricsService metrics;

    @Autowired
    public TaskLifecycleService(
            TaskOrchestrationFacade facade,
            LifecycleProperties properties,
            TaskLifecycleRuntimeConfigurationView runtimeConfiguration) {
        this.facade = facade;
        this.properties = properties;
        this.runtimeConfiguration = runtimeConfiguration;
    }

    /** Compatibility/test constructor; production Spring wiring uses the runtime-aware constructor. */
    public TaskLifecycleService(TaskOrchestrationFacade facade, LifecycleProperties properties) {
        this.facade = facade;
        this.properties = properties;
        this.runtimeConfiguration = null;
    }

    public TaskRecord timeout(String taskId,String reason){return facade.timeoutTask(taskId,firstNonBlank(reason,"Manually timed out"),OffsetDateTime.now(ZoneOffset.UTC));}
    public TaskRecord cancel(String taskId,String reason){return facade.cancelTask(taskId,firstNonBlank(reason,"Manually cancelled"),OffsetDateTime.now(ZoneOffset.UTC));}
    public TaskRecord reassign(String taskId,String reason){return facade.reassignTask(taskId,firstNonBlank(reason,"Manually reassigned"),OffsetDateTime.now(ZoneOffset.UTC));}
    public TaskRecord hold(String taskId,String reason){return facade.holdTask(taskId,firstNonBlank(reason,"Held by Security Incident control"),OffsetDateTime.now(ZoneOffset.UTC));}
    public TaskRecord resume(String taskId,String reason){return facade.resumeTask(taskId,firstNonBlank(reason,"Released from Security Incident hold"),OffsetDateTime.now(ZoneOffset.UTC));}
    public TaskRecord forceFail(String taskId,String reason){return facade.forceFailTask(taskId,firstNonBlank(reason,"Force-failed by Security Incident operator"),OffsetDateTime.now(ZoneOffset.UTC));}

    public LifecycleScanResult processTimeoutsAndReassignments() {
        boolean timeoutEnabled = runtimeConfiguration == null ? properties.getTask().isTimeoutEnabled() : runtimeConfiguration.timeoutEnabled();
        boolean autoReassignEnabled = runtimeConfiguration == null ? properties.getTask().isAutoReassignEnabled() : runtimeConfiguration.autoReassignEnabled();
        var createdTimeout = runtimeConfiguration == null ? properties.getTask().getCreatedTimeout() : runtimeConfiguration.createdTimeout();
        var assignedTimeout = runtimeConfiguration == null ? properties.getTask().getAssignedTimeout() : runtimeConfiguration.assignedTimeout();
        var dispatchedTimeout = runtimeConfiguration == null ? properties.getTask().getDispatchedTimeout() : runtimeConfiguration.dispatchedTimeout();
        var runningTimeout = runtimeConfiguration == null ? properties.getTask().getRunningTimeout() : runtimeConfiguration.runningTimeout();
        int maxReassignments = runtimeConfiguration == null ? properties.getTask().getMaxReassignments() : runtimeConfiguration.maxReassignments();
        int maxBatchSize = runtimeConfiguration == null ? properties.getTask().getMaxBatchSize() : runtimeConfiguration.maxBatchSize();
        long scanIntervalMs = runtimeConfiguration == null ? properties.getTask().getScanIntervalMs() : runtimeConfiguration.scanInterval().toMillis();

        TaskLifecyclePolicy policy = new TaskLifecyclePolicy(
                timeoutEnabled,
                autoReassignEnabled,
                createdTimeout,
                assignedTimeout,
                dispatchedTimeout,
                runningTimeout,
                maxReassignments,
                maxBatchSize);

        log.debug("task_lifecycle_policy_loaded authority={} timeoutEnabled={} autoReassignEnabled={} scanIntervalMs={} createdTimeout={} assignedTimeout={} dispatchedTimeout={} runningTimeout={} maxReassignments={} maxBatchSize={}",
                runtimeConfiguration == null ? "STARTUP_COMPATIBILITY" : (runtimeConfiguration.runtimeBacked() ? "RUNTIME_SNAPSHOT_OR_PRECUTOVER_FALLBACK" : "STARTUP_PRECUTOVER_FALLBACK"),
                timeoutEnabled, autoReassignEnabled, scanIntervalMs, createdTimeout, assignedTimeout,
                dispatchedTimeout, runningTimeout, maxReassignments, maxBatchSize);

        LifecycleScanResult result = facade.processLifecycle(policy,OffsetDateTime.now(ZoneOffset.UTC));
        if(metrics!=null)metrics.recordLifecycleScan("task",result);
        return result;
    }

    private String firstNonBlank(String... values){if(values==null)return null;for(String v:values)if(v!=null&&!v.isBlank())return v;return null;}
}
