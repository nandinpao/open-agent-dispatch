package com.opensocket.aievent.core.integration.issue.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

class V405IssueProjectionRuntimeConfigurationViewTest {
    @Test
    void shouldReadIssuePilotValuesFromNewestSnapshot() {
        RuntimeConfigurationLocalSnapshotRegistry registry=new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values=new RuntimeConfigurationSnapshotValues(registry,JsonMapper.builder().build());
        IssueProjectionRuntimeConfigurationView view=new IssueProjectionRuntimeConfigurationView(new IssueProjectionProperties(),values,new RuntimeConfigurationAuthorityRegistry());
        registry.atomicSwap(snapshot("issue-1",1,50,15));
        assertThat(view.reconcileBatchSize()).isEqualTo(50);
        assertThat(view.retryDelaySeconds()).isEqualTo(15);
        registry.atomicSwap(snapshot("issue-2",2,80,30));
        assertThat(view.reconcileBatchSize()).isEqualTo(80);
        assertThat(view.retryDelaySeconds()).isEqualTo(30);
    }
    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision,long sequence,int batch,long retry){
        OffsetDateTime now=OffsetDateTime.of(2026,9,18,0,0,0,0,ZoneOffset.UTC);
        String payload="{\"issue-projection.reconcile-batch-size\":"+batch+",\"issue-projection.retry-delay-seconds\":"+retry+"}";
        return new RuntimeConfigurationSnapshotEnvelope("LOCAL","config-set-issue",RuntimeConfigurationSetKeys.ISSUE_SYSTEM,revision,sequence,2,now,now.plusHours(1),"CORE",payload,"hash","signature");
    }
}
