package com.opensocket.aievent.core.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTaskLifecycle {
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskLifecycle.class);

    private final TaskLifecycleService service;

    public ScheduledTaskLifecycle(TaskLifecycleService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${core.lifecycle.task.scan-interval-ms:30000}")
    public void processTimeoutsAndReassignments() {
        log.debug("task_lifecycle_scan_started reason=SCHEDULED");
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
