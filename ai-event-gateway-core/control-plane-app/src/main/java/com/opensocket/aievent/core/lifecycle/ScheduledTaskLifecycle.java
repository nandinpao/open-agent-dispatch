package com.opensocket.aievent.core.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;

/** V41-C3B1 dynamic Task lifecycle scheduler. */
@Component
public class ScheduledTaskLifecycle implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskLifecycle.class);

    private final TaskLifecycleService service;
    private final DynamicFixedDelayTask dynamicTask;

    public ScheduledTaskLifecycle(
            TaskLifecycleService service,
            TaskLifecycleRuntimeConfigurationView runtimeConfiguration,
            @Qualifier("maintenanceOperationalScheduler") TaskScheduler scheduler) {
        this.service = service;
        this.dynamicTask = new DynamicFixedDelayTask(
                scheduler,
                "task-lifecycle",
                this::processTimeoutsAndReassignments,
                runtimeConfiguration::scanInterval);
    }

    @Override public void afterPropertiesSet() { dynamicTask.start(); }
    @Override public void destroy() { dynamicTask.stop(); }

    public void processTimeoutsAndReassignments() {
        log.debug("task_lifecycle_scan_started reason=DYNAMIC_SCHEDULED");
        LifecycleScanResult result = service.processTimeoutsAndReassignments();
        if (result == null) {
            log.warn("task_lifecycle_scan_completed result=NULL");
            return;
        }
        if (result.getUpdated() > 0 || result.getReassigned() > 0 || result.getTimedOut() > 0) {
            log.info("task_lifecycle_scan_completed scanned={} updated={} reassigned={} timedOut={} message={}",
                    result.getScanned(), result.getUpdated(), result.getReassigned(), result.getTimedOut(), result.getMessage());
        } else {
            log.debug("task_lifecycle_scan_completed scanned={} updated={} reassigned={} timedOut={} message={}",
                    result.getScanned(), result.getUpdated(), result.getReassigned(), result.getTimedOut(), result.getMessage());
        }
    }
}
