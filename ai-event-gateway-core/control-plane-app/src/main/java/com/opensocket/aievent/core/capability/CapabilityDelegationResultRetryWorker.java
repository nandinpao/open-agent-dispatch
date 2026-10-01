package com.opensocket.aievent.core.capability;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.capability.runtime.A2ADelegationRuntimeConfigurationView;

/** Stage 5 bounded multi-tenant durable retry worker. */
@Component
public class CapabilityDelegationResultRetryWorker {
    private final JdbcTemplate jdbc;
    private final CapabilityDelegationResultRetryService retry;
    private final CapabilityDelegationResultNotifier notifier;
    private final CapabilityDelegationResultNotificationRecorder recorder;
    private final String workerId;
    private final A2ADelegationRuntimeConfigurationView runtimeConfiguration;

    public CapabilityDelegationResultRetryWorker(JdbcTemplate jdbc, CapabilityDelegationResultRetryService retry,
            CapabilityDelegationResultNotifier notifier, CapabilityDelegationResultNotificationRecorder recorder,
            A2ADelegationRuntimeConfigurationView runtimeConfiguration,
            @Value("${opendispatch.capability-result-retry.worker-id:cap-result-retry-worker}") String workerId) {
        this.jdbc=jdbc; this.retry=retry; this.notifier=notifier; this.recorder=recorder;
        this.workerId=workerId; this.runtimeConfiguration=runtimeConfiguration;
    }

    public void retryDue() {
        if (!runtimeConfiguration.resultRetryEnabled()) return;
        int batchSize = runtimeConfiguration.resultRetryBatchSize();
        List<String> tenants=jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class);
        for(String tenant:tenants) {
            List<CapabilityDelegationResultNotification> notifications=retry.claimDue(tenant,workerId,batchSize);
            for(CapabilityDelegationResultNotification n:notifications) {
                CapabilityDelegationResultNotifier.NotificationDeliveryResult delivery=notifier.deliver(n);
                recorder.record(n,delivery);
            }
        }
    }
}
