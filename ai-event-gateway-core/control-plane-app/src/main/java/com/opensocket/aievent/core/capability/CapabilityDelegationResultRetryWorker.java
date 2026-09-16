package com.opensocket.aievent.core.capability;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Stage 5 bounded multi-tenant durable retry worker. */
@Component
@ConditionalOnProperty(name="opendispatch.capability-result-retry.enabled",havingValue="true",matchIfMissing=true)
public class CapabilityDelegationResultRetryWorker {
    private final JdbcTemplate jdbc;
    private final CapabilityDelegationResultRetryService retry;
    private final CapabilityDelegationResultNotifier notifier;
    private final CapabilityDelegationResultNotificationRecorder recorder;
    private final String workerId;
    private final int batchSize;

    public CapabilityDelegationResultRetryWorker(JdbcTemplate jdbc, CapabilityDelegationResultRetryService retry,
            CapabilityDelegationResultNotifier notifier, CapabilityDelegationResultNotificationRecorder recorder,
            @Value("${opendispatch.capability-result-retry.worker-id:cap-result-retry-worker}") String workerId,
            @Value("${opendispatch.capability-result-retry.batch-size:50}") int batchSize) {
        this.jdbc=jdbc; this.retry=retry; this.notifier=notifier; this.recorder=recorder;
        this.workerId=workerId; this.batchSize=Math.max(1,Math.min(batchSize,100));
    }

    @Scheduled(fixedDelayString="${opendispatch.capability-result-retry.poll-ms:5000}", scheduler="reconciliationOperationalScheduler")
    public void retryDue() {
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
