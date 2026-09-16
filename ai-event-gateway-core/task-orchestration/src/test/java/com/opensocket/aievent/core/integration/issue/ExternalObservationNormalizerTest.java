package com.opensocket.aievent.core.integration.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ExternalObservationNormalizerTest {
    private final ExternalObservationNormalizer normalizer = new ExternalObservationNormalizer(new ObjectMapper());

    @Test
    void shouldProduceStableCanonicalHashForEquivalentJson() {
        var first = normalizer.normalize("{\"status\":\"OPEN\",\"priority\":1.00,\"labels\":[\"a\",\"b\"]}");
        var second = normalizer.normalize("{ \"labels\" : [\"a\",\"b\"], \"priority\":1, \"status\":\"OPEN\" }");

        assertThat(first.canonicalJson()).isEqualTo(second.canonicalJson());
        assertThat(first.canonicalHash()).isEqualTo(second.canonicalHash());
        assertThat(first.profileVersion()).isEqualTo(ExternalObservationNormalizer.PROFILE_VERSION);
    }

    @Test
    void shouldRejectSensitiveKeysIndependentOfCaseOrSeparator() {
        assertThatThrownBy(() -> normalizer.normalize("{\"nested\":{\"accessToken\":\"secret-value\"}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEBHOOK_SENSITIVE_FIELD_BLOCKED");
        assertThatThrownBy(() -> normalizer.assertNoSensitiveKeys("{\"private-key\":\"secret-value\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEBHOOK_SENSITIVE_FIELD_BLOCKED");
    }
}
