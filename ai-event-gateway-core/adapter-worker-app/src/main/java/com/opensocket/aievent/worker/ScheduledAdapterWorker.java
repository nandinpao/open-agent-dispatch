package com.opensocket.aievent.worker;

import com.opensocket.aievent.service.adapter.AdapterWorkItem;
import com.opensocket.aievent.worker.configuration.AdapterWorkerRuntimeConfigurationView;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ScheduledAdapterWorker implements InitializingBean, DisposableBean {
    private final AdapterWorkerRuntimeConfigurationView runtimeConfiguration;
    private final CoreAdapterActionClient client;
    private final List<AdapterWorkExecutor> executors;
    private final TaskScheduler taskScheduler;
    private final ObservationRegistry observationRegistry;
    private final WorkerDynamicFixedDelayTask dynamicTask;

    public ScheduledAdapterWorker(AdapterWorkerRuntimeConfigurationView runtimeConfiguration,CoreAdapterActionClient client,
            List<AdapterWorkExecutor> executors,TaskScheduler taskScheduler,ObservationRegistry observationRegistry){
        this.runtimeConfiguration=runtimeConfiguration;this.client=client;this.executors=executors;this.taskScheduler=taskScheduler;this.observationRegistry=observationRegistry;
        this.dynamicTask=new WorkerDynamicFixedDelayTask(taskScheduler,"adapter-worker-poll",this::poll,runtimeConfiguration::pollInterval);
    }
    @Override public void afterPropertiesSet(){dynamicTask.start();}
    @Override public void destroy(){dynamicTask.stop();}
    public void poll(){
        if(!runtimeConfiguration.enabled())return;
        for(String type:runtimeConfiguration.adapterTypes())if(runtimeConfiguration.isConfigured(type))client.claim(type).ifPresent(this::execute);
    }
    private void execute(AdapterWorkItem item){
        try(LeaseHeartbeatGuard ignored=new LeaseHeartbeatGuard(taskScheduler,observationRegistry,client,item,runtimeConfiguration.leaseSeconds())){
            AdapterWorkResult result=executors.stream().filter(executor->executor.supports(item)).findFirst().map(executor->executor.execute(item)).orElseGet(()->AdapterWorkResult.failure("No executor for "+item.adapterType(),false));
            if(result.success())client.complete(item,result);else client.fail(item,result);
        }
    }
}
