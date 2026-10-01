package com.opensocket.aievent.core.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

class V405TaskRuntimeConfigurationViewTest {
    @Test
    void shouldChangeTaskRecoveryDelayFromNewerLocalSnapshot() {
        RuntimeConfigurationLocalSnapshotRegistry registry=new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values=new RuntimeConfigurationSnapshotValues(registry,JsonMapper.builder().build());
        TaskDispatchRecoveryRuntimeConfigurationView view=new TaskDispatchRecoveryRuntimeConfigurationView(new TaskDispatchRecoveryProperties(),values);
        registry.atomicSwap(snapshot("task-1",1,"PT5S","PT20S"));
        assertThat(view.delayForAttempt(2)).isEqualTo(Duration.ofSeconds(10));
        registry.atomicSwap(snapshot("task-2",2,"PT10S","PT1M"));
        assertThat(view.delayForAttempt(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(view.revisionId()).isEqualTo("task-2");
    }
    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision,long sequence,String initial,String max){
        OffsetDateTime now=OffsetDateTime.of(2026,9,18,0,0,0,0,ZoneOffset.UTC);
        String payload="{\"task.dispatch-recovery.max-batch-size\":100,\"task.dispatch-recovery.max-attempts\":10,\"task.dispatch-recovery.initial-delay\":\""+initial+"\",\"task.dispatch-recovery.max-delay\":\""+max+"\"}";
        return new RuntimeConfigurationSnapshotEnvelope("LOCAL","config-set-task",RuntimeConfigurationSetKeys.TASK_SYSTEM,revision,sequence,2,now,now.plusHours(1),"CORE",payload,"hash","signature");
    }
}
