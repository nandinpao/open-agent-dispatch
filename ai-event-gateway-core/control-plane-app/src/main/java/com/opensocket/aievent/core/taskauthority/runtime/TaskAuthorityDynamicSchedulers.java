package com.opensocket.aievent.core.taskauthority.runtime;

import org.springframework.beans.factory.DisposableBean;import org.springframework.beans.factory.InitializingBean;import org.springframework.beans.factory.annotation.Qualifier;import org.springframework.scheduling.TaskScheduler;import org.springframework.stereotype.Component;import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;import com.opensocket.aievent.core.taskauthority.*;
@Component public final class TaskAuthorityDynamicSchedulers implements InitializingBean,DisposableBean {
 private final DynamicFixedDelayTask conditions,finalization,projection;
 public TaskAuthorityDynamicSchedulers(TaskAuthorityRuntimeConfigurationView runtime,TaskConditionTimeoutWorker conditionsWorker,TaskFinalizationWorker finalizationWorker,TaskFinalizationProjectionWorker projectionWorker,@Qualifier("maintenanceOperationalScheduler") TaskScheduler maintenance,@Qualifier("projectionOperationalScheduler") TaskScheduler projectionScheduler){conditions=new DynamicFixedDelayTask(maintenance,"task-condition-timeout",conditionsWorker::run,runtime::conditionsPollDelay);finalization=new DynamicFixedDelayTask(maintenance,"task-finalization",finalizationWorker::run,runtime::finalizationPollDelay);projection=new DynamicFixedDelayTask(projectionScheduler,"task-finalization-projection",projectionWorker::run,runtime::projectionPollDelay);}
 public void afterPropertiesSet(){conditions.start();finalization.start();projection.start();} public void destroy(){conditions.stop();finalization.stop();projection.stop();}
}
