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
    @Autowired(required=false) private CoreMetricsService metrics;
    public TaskLifecycleService(TaskOrchestrationFacade facade,LifecycleProperties properties){this.facade=facade;this.properties=properties;}
    public TaskRecord timeout(String taskId,String reason){return facade.timeoutTask(taskId,firstNonBlank(reason,"Manually timed out"),OffsetDateTime.now(ZoneOffset.UTC));}
    public TaskRecord cancel(String taskId,String reason){return facade.cancelTask(taskId,firstNonBlank(reason,"Manually cancelled"),OffsetDateTime.now(ZoneOffset.UTC));}
    public TaskRecord reassign(String taskId,String reason){return facade.reassignTask(taskId,firstNonBlank(reason,"Manually reassigned"),OffsetDateTime.now(ZoneOffset.UTC));}
    public LifecycleScanResult processTimeoutsAndReassignments(){
        var p=properties.getTask();
        TaskLifecyclePolicy policy=new TaskLifecyclePolicy(p.isTimeoutEnabled(),p.isAutoReassignEnabled(),p.getCreatedTimeout(),p.getAssignedTimeout(),p.getDispatchedTimeout(),p.getRunningTimeout(),p.getMaxReassignments(),p.getMaxBatchSize());
        log.debug("task_lifecycle_policy_loaded timeoutEnabled={} autoReassignEnabled={} scanIntervalMs={} createdTimeout={} assignedTimeout={} dispatchedTimeout={} runningTimeout={} maxReassignments={} maxBatchSize={}", p.isTimeoutEnabled(), p.isAutoReassignEnabled(), p.getScanIntervalMs(), p.getCreatedTimeout(), p.getAssignedTimeout(), p.getDispatchedTimeout(), p.getRunningTimeout(), p.getMaxReassignments(), p.getMaxBatchSize());
        LifecycleScanResult result=facade.processLifecycle(policy,OffsetDateTime.now(ZoneOffset.UTC)); if(metrics!=null)metrics.recordLifecycleScan("task",result); return result;
    }
    private String firstNonBlank(String... values){if(values==null)return null;for(String v:values)if(v!=null&&!v.isBlank())return v;return null;}
}
