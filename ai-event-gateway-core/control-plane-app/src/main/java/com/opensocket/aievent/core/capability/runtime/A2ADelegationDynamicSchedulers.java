package com.opensocket.aievent.core.capability.runtime;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.a2a.application.service.ScheduledA2AReconciliationPipeline;
import com.opensocket.aievent.core.capability.A2AAuthorityRevocationSweep;
import com.opensocket.aievent.core.capability.A2APushHandoffWorker;
import com.opensocket.aievent.core.capability.A2ARemoteReadExecutionWorker;
import com.opensocket.aievent.core.capability.A2ARemoteTrackingWorker;
import com.opensocket.aievent.core.capability.CapabilityDelegationResultRetryWorker;
import com.opensocket.aievent.core.capability.GovernedPlanExecutionRuntimeWorker;
import com.opensocket.aievent.core.capability.McpReadExecutionWorker;
import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;

/** Central C3R2G next-cycle scheduler owner for active A2A/delegation workers. */
@Component
public final class A2ADelegationDynamicSchedulers implements InitializingBean, DisposableBean {
    private final DynamicFixedDelayTask remoteTracking;
    private final DynamicFixedDelayTask pushHandoff;
    private final DynamicFixedDelayTask remoteRead;
    private final DynamicFixedDelayTask resultRetry;
    private final DynamicFixedDelayTask mcpRead;
    private final DynamicFixedDelayTask planRuntime;
    private final DynamicFixedDelayTask authorityRevocation;
    private final DynamicFixedDelayTask reconciliationPipeline;

    public A2ADelegationDynamicSchedulers(
            A2ADelegationRuntimeConfigurationView runtime,
            A2ARemoteTrackingWorker remoteTrackingWorker,
            A2APushHandoffWorker pushHandoffWorker,
            A2ARemoteReadExecutionWorker remoteReadWorker,
            CapabilityDelegationResultRetryWorker resultRetryWorker,
            McpReadExecutionWorker mcpReadWorker,
            GovernedPlanExecutionRuntimeWorker planRuntimeWorker,
            A2AAuthorityRevocationSweep authorityRevocationSweep,
            ScheduledA2AReconciliationPipeline reconciliationPipeline,
            @Qualifier("a2aRemoteOperationalScheduler") TaskScheduler a2aScheduler,
            @Qualifier("reconciliationOperationalScheduler") TaskScheduler reconciliationScheduler) {
        this.remoteTracking = new DynamicFixedDelayTask(a2aScheduler, "a2a-remote-tracking", remoteTrackingWorker::run, runtime::asyncPollDelay);
        this.pushHandoff = new DynamicFixedDelayTask(a2aScheduler, "a2a-push-handoff", pushHandoffWorker::run, runtime::pushHandoffDelay);
        this.remoteRead = new DynamicFixedDelayTask(a2aScheduler, "a2a-remote-read", remoteReadWorker::run, runtime::readPollDelay);
        this.resultRetry = new DynamicFixedDelayTask(reconciliationScheduler, "capability-result-retry", resultRetryWorker::retryDue, runtime::resultRetryPollDelay);
        this.mcpRead = new DynamicFixedDelayTask(reconciliationScheduler, "mcp-read-execution", mcpReadWorker::run, runtime::mcpReadPollDelay);
        this.planRuntime = new DynamicFixedDelayTask(reconciliationScheduler, "governed-plan-runtime", planRuntimeWorker::run, runtime::planRuntimePollDelay);
        this.authorityRevocation = new DynamicFixedDelayTask(a2aScheduler, "a2a-authority-revocation", authorityRevocationSweep::run, runtime::authorityRevocationSweepDelay);
        this.reconciliationPipeline = new DynamicFixedDelayTask(reconciliationScheduler, "a2a-reconciliation-pipeline", reconciliationPipeline::run, runtime::reconciliationPipelineDelay);
    }

    @Override public void afterPropertiesSet() {
        remoteTracking.start(); pushHandoff.start(); remoteRead.start(); resultRetry.start();
        mcpRead.start(); planRuntime.start(); authorityRevocation.start(); reconciliationPipeline.start();
    }

    @Override public void destroy() {
        remoteTracking.stop(); pushHandoff.stop(); remoteRead.stop(); resultRetry.stop();
        mcpRead.stop(); planRuntime.stop(); authorityRevocation.stop(); reconciliationPipeline.stop();
    }
}
