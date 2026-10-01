package com.opensocket.aievent.core.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWave;

class V41C3R3ACutoverWaveContractTest {
    @Test
    void waveRequiresAuthorityContractV2OrLater() {
        assertThatThrownBy(() -> new RuntimeConfigurationCutoverWave("W1", 1, "wave", "LOW", 1, "stage",
                List.of(new RuntimeConfigurationCutoverWave.Member("RUNTIME/X/SYSTEM", 1, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v2");
    }

    @Test
    void waveRejectsDuplicateConfigSetMembership() {
        assertThatThrownBy(() -> new RuntimeConfigurationCutoverWave("W1", 1, "wave", "LOW", 2, "stage",
                List.of(
                        new RuntimeConfigurationCutoverWave.Member("RUNTIME/X/SYSTEM", 1, 1),
                        new RuntimeConfigurationCutoverWave.Member("RUNTIME/X/SYSTEM", 2, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void validWavePreservesExpectedKeyCounts() {
        var wave = new RuntimeConfigurationCutoverWave("W1", 1, "wave", "LOW", 2, "stage",
                List.of(
                        new RuntimeConfigurationCutoverWave.Member("RUNTIME/A/SYSTEM", 1, 3),
                        new RuntimeConfigurationCutoverWave.Member("RUNTIME/B/SYSTEM", 2, 5)));
        assertThat(wave.members()).hasSize(2);
        assertThat(wave.members().stream().mapToInt(RuntimeConfigurationCutoverWave.Member::expectedRuntimeKeyCount).sum()).isEqualTo(8);
    }
}
