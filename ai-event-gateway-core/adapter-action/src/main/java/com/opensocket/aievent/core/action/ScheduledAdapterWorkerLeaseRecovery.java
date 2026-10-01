package com.opensocket.aievent.core.action;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;

@Component
public class ScheduledAdapterWorkerLeaseRecovery implements InitializingBean, DisposableBean {
    private final AdapterActionService service;
    private final AdapterActionWorkerRuntimeConfigurationView workerRuntimeConfiguration;
    private final DynamicFixedDelayTask dynamicTask;

    public ScheduledAdapterWorkerLeaseRecovery(AdapterActionService service,
            AdapterActionWorkerRuntimeConfigurationView workerRuntimeConfiguration,
            @Qualifier("maintenanceOperationalScheduler") TaskScheduler scheduler) {
        this.service=service;this.workerRuntimeConfiguration=workerRuntimeConfiguration;
        this.dynamicTask=new DynamicFixedDelayTask(scheduler,"adapter-action-worker-lease-recovery",
                this::recoverExpiredLeases,workerRuntimeConfiguration::expiredLeaseScanInterval);
    }
    @Override public void afterPropertiesSet(){dynamicTask.start();}
    @Override public void destroy(){dynamicTask.stop();}
    public void recoverExpiredLeases(){service.recoverExpiredWorkerLeases(workerRuntimeConfiguration.expiredLeaseScanBatchSize());}
}
