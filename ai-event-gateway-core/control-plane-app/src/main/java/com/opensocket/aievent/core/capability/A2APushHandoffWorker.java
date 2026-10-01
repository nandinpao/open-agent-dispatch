package com.opensocket.aievent.core.capability;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.capability.runtime.A2ADelegationRuntimeConfigurationView;

/** C0-C2 durable PUSH handoff consumer. HTTP ingress location is never lifecycle authority. */
@Component
public class A2APushHandoffWorker {
    private final JdbcTemplate tenantJdbc;
    private final A2APushInboxService inbox;
    private final A2ARemoteAuthorityService authority;
    private final A2ADelegationRuntimeConfigurationView runtimeConfiguration;

    public A2APushHandoffWorker(
            JdbcTemplate tenantJdbc,
            A2APushInboxService inbox,
            A2ARemoteAuthorityService authority,
            A2ADelegationRuntimeConfigurationView runtimeConfiguration) {
        this.tenantJdbc = tenantJdbc;
        this.inbox = inbox;
        this.authority = authority;
        this.runtimeConfiguration = runtimeConfiguration;
    }

    public void run() {
        if (!runtimeConfiguration.asyncEnabled()) return;
        int batchSize = runtimeConfiguration.pushHandoffBatchSize();
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
