package com.opensocket.aievent.core.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverPlan;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverState;

class V41C3R2X4GenericCutoverContractTest {
    @Test
    void authorityFingerprintChangesWhenRuntimeOnlyManifestChanges() {
        String payload="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String a=RuntimeConfigurationGenericCutoverService.fingerprintFor(payload,"RUNTIME_ONLY",Set.of("a","b"));
        String b=RuntimeConfigurationGenericCutoverService.fingerprintFor(payload,"RUNTIME_ONLY",Set.of("a","c"));
        assertThat(a).hasSize(64).isNotEqualTo(b);
    }

    @Test
    void cutoverPlanRejectsContractV1() {
        assertThatThrownBy(()->new RuntimeConfigurationCutoverPlan("c","s","RUNTIME/X/SYSTEM","LOCAL","r",1,"RUNTIME_ONLY",
                Set.of("x"),"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",RuntimeConfigurationCutoverState.PREPARED,
                "operator","reason",OffsetDateTime.now(),null,null,1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v2");
    }
}
