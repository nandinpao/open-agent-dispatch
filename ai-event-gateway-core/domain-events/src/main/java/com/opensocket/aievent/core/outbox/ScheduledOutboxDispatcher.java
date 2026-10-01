package com.opensocket.aievent.core.outbox;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;

@Component
@ConditionalOnProperty(prefix="core.outbox",name="dispatcher-enabled",havingValue="true",matchIfMissing=true)
public class ScheduledOutboxDispatcher implements InitializingBean, DisposableBean {
    private final DynamicFixedDelayTask task;
    public ScheduledOutboxDispatcher(OutboxEventDispatcher dispatcher,
                                     OutboxRuntimeConfigurationView runtimeConfiguration,
                                     @Qualifier("projectionOperationalScheduler") TaskScheduler scheduler) {
        this.task = new DynamicFixedDelayTask(scheduler,"core-outbox-dispatch",dispatcher::dispatchPending,runtimeConfiguration::scanInterval);
    }
    @Override public void afterPropertiesSet(){task.start();}
    @Override public void destroy(){task.stop();}
}
