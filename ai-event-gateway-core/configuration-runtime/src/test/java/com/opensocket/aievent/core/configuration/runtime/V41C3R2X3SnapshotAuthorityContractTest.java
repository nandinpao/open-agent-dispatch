package com.opensocket.aievent.core.configuration.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2X3SnapshotAuthorityContractTest {
    @Test
    void authorityOnlyChangeChangesSnapshotFingerprint() {
        var dual=snapshot("DUAL_READ",Set.of(),"{\"k\":1}");
        var runtime=snapshot("RUNTIME_ONLY",Set.of("k"),"{\"k\":1}");
        assertThat(dual.payloadHash()).isEqualTo(runtime.payloadHash());
        assertThat(dual.snapshotFingerprint()).isNotEqualTo(runtime.snapshotFingerprint());
    }

    @Test
    void requiredKeyCannotSilentlyFallBackWhenSnapshotIsPresent() {
        RuntimeConfigurationLocalSnapshotRegistry registry=new RuntimeConfigurationLocalSnapshotRegistry();
        registry.atomicSwap(snapshot("DUAL_READ",Set.of("required.key"),"{}"));
        RuntimeConfigurationSnapshotValues values=new RuntimeConfigurationSnapshotValues(registry,JsonMapper.builder().build());
        assertThatThrownBy(() -> values.longValue("RUNTIME/TEST/SYSTEM","required.key"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CONFIGURATION_INCOMPLETE");
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String mode,Set<String> required,String payload){
        OffsetDateTime now=OffsetDateTime.of(2026,9,24,12,0,0,0,ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope("LOCAL","set-test","RUNTIME/TEST/SYSTEM","r1",1,1,now,now.plusHours(1),"CORE",mode,required,payload,"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","sig");
    }
}
