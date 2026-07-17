package com.opensocket.aievent.core.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.event.NormalizedEvent;

class EventNormalizerOptionalFieldsTest {

    private final EventNormalizer normalizer = new EventNormalizer();

    @Test
    void shouldKeepAbsentOptionalRoutingFieldsNullExceptClassificationSentinels() {
        EventIntakeRequest request = new EventIntakeRequest();
        request.setTenantId("tenant-a");
        request.setSourceSystem("source-a");
        request.setObjectType("payment");
        request.setObjectId("pay-001");
        request.setEventType("payment_blocked");
        request.setSeverity("CRITICAL");

        NormalizedEvent event = normalizer.normalize(request);

        assertThat(event.targetSystem()).isNull();
        assertThat(event.errorCode()).isEqualTo("UNKNOWN");
        assertThat(event.requestedSkill()).isNull();
        assertThat(event.handoffMode()).isNull();
        assertThat(event.correlationId()).isNull();
        assertThat(event.parentTaskId()).isNull();
    }


    @Test
    void shouldAcceptSourceSystemOnlyIntakeAndNormalizeUnknownClassification() {
        EventIntakeRequest request = new EventIntakeRequest();
        request.setTenantId("tenant-a");
        request.setSourceSystem("erp");

        NormalizedEvent event = normalizer.normalize(request);

        assertThat(event.sourceSystem()).isEqualTo("ERP");
        assertThat(event.objectType()).isEqualTo("UNKNOWN");
        assertThat(event.eventType()).isEqualTo("UNKNOWN");
        assertThat(event.errorCode()).isEqualTo("UNKNOWN");
        assertThat(event.severity().name()).isEqualTo("MEDIUM");
    }

    @Test
    void shouldNormalizeProvidedOptionalRoutingFields() {
        EventIntakeRequest request = new EventIntakeRequest();
        request.setTenantId("tenant-a");
        request.setSourceSystem("source-a");
        request.setObjectType("payment");
        request.setObjectId("pay-001");
        request.setEventType("payment_blocked");
        request.setSeverity("CRITICAL");
        request.setTargetSystem(" risk system ");
        request.setErrorCode(" risk blocked ");
        request.setRequestedSkill(" payment analysis ");
        request.setHandoffMode(" review only ");
        request.setCorrelationId(" corr 001 ");
        request.setParentTaskId(" task parent ");

        NormalizedEvent event = normalizer.normalize(request);

        assertThat(event.targetSystem()).isEqualTo("RISK_SYSTEM");
        assertThat(event.errorCode()).isEqualTo("RISK_BLOCKED");
        assertThat(event.requestedSkill()).isEqualTo("PAYMENT_ANALYSIS");
        assertThat(event.handoffMode()).isEqualTo("REVIEW_ONLY");
        assertThat(event.correlationId()).isEqualTo("corr_001");
        assertThat(event.parentTaskId()).isEqualTo("task_parent");
    }
}
