package com.opensocket.aievent.core.capability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** C0-C2 durable PUSH handoff consumer. HTTP ingress location is never lifecycle authority. */
@Component
@ConditionalOnProperty(name = "opendispatch.a2a-async.enabled", havingValue = "true", matchIfMissing = true)
public class A2APushHandoffWorker {
    private final JdbcTemplate tenantJdbc;
    private final A2APushInboxService inbox;
    private final A2ARemoteAuthorityService authority;
    private final int batchSize;

    public A2APushHandoffWorker(
            JdbcTemplate tenantJdbc,
            A2APushInboxService inbox,
            A2ARemoteAuthorityService authority,
            @org.springframework.beans.factory.annotation.Value("${opendispatch.a2a-async.push-handoff-batch-size:20}") int batchSize) {
        this.tenantJdbc = tenantJdbc;
        this.inbox = inbox;
        this.authority = authority;
        this.batchSize = Math.max(1, Math.min(batchSize, 50));
    }

    @Scheduled(fixedDelayString = "${opendispatch.a2a-async.push-handoff-ms:1000}", scheduler = "a2aRemoteOperationalScheduler")
    public void run() {
        for (String tenant : tenantJdbc.queryForList(
                "select tenant_id from tenants where status='ACTIVE' order by tenant_id", String.class)) {
            for (A2APushInboxService.PushHandoff handoff : inbox.claimForOwner(tenant, authority.instanceId(), batchSize)) {
                try {
                    inbox.processClaimed(tenant, authority.instanceId(), handoff);
                } catch (Exception ex) {
                    inbox.releaseClaim(tenant, handoff, "PUSH_HANDOFF_FAILED:" + safe(ex.getMessage()));
                }
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
