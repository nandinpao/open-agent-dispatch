package com.opensocket.aievent.core.lifecycle;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;
import com.opensocket.aievent.core.incident.IncidentRuntimeConfigurationView;

@Component
public class ScheduledIncidentLifecycle implements InitializingBean, DisposableBean {
    private final IncidentLifecycleService service;
    private final DynamicFixedDelayTask task;

    public ScheduledIncidentLifecycle(IncidentLifecycleService service,
                                      IncidentRuntimeConfigurationView runtimeConfiguration,
                                      @Qualifier("maintenanceOperationalScheduler") TaskScheduler scheduler) {
        this.service = service;
        this.task = new DynamicFixedDelayTask(scheduler, "incident-lifecycle", service::autoResolveStaleIncidents, runtimeConfiguration::scanInterval);
    }
    @Override public void afterPropertiesSet(){task.start();}
    @Override public void destroy(){task.stop();}
}
