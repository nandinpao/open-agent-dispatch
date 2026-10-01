package com.opensocket.aievent.core.integration;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;

@Component
public class ScheduledIntegrationEventDelivery implements InitializingBean, DisposableBean {
    private final DynamicFixedDelayTask task;
    public ScheduledIntegrationEventDelivery(IntegrationEventDeliveryService service,
                                             IntegrationEventsRuntimeConfigurationView runtimeConfiguration,
                                             @Qualifier("projectionOperationalScheduler") TaskScheduler scheduler){
        this.task=new DynamicFixedDelayTask(scheduler,"integration-event-delivery",service::deliverPending,runtimeConfiguration::scanInterval);
    }
    @Override public void afterPropertiesSet(){task.start();}
    @Override public void destroy(){task.stop();}
}
