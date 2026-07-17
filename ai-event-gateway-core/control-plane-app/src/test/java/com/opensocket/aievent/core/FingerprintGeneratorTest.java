package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.fingerprint.DynamicTokenMasker;
import com.opensocket.aievent.core.fingerprint.FingerprintFieldResolver;
import com.opensocket.aievent.core.fingerprint.FingerprintGenerator;
import com.opensocket.aievent.core.fingerprint.FingerprintPolicyProperties;
import com.opensocket.aievent.core.fingerprint.FingerprintPolicyResolver;
import com.opensocket.aievent.core.normalize.EventNormalizer;

class FingerprintGeneratorTest {
    @Test
    void sameNormalizedEventShouldGenerateSameFingerprint() {
        EventNormalizer normalizer = new EventNormalizer();
        FingerprintGenerator generator = new FingerprintGenerator();

        EventIntakeRequest a = request("Chamber temperature over threshold");
        EventIntakeRequest b = request("  Chamber   temperature over threshold  ");

        String fa = generator.generate(normalizer.normalize(a));
        String fb = generator.generate(normalizer.normalize(b));

        assertThat(fa).isEqualTo(fb);
    }

    @Test
    void dynamicMessageTokensShouldNotSplitSameRootCause() {
        EventNormalizer normalizer = new EventNormalizer();
        FingerprintGenerator generator = new FingerprintGenerator();

        EventIntakeRequest a = request("Order MO202606100001 failed at 10:03:21 from host 10.10.1.25");
        EventIntakeRequest b = request("Order MO202606100999 failed at 11:58:02 from host 10.10.9.88");

        String fa = generator.generate(normalizer.normalize(a));
        String fb = generator.generate(normalizer.normalize(b));

        assertThat(fa).isEqualTo(fb);
    }

    @Test
    void sourceSpecificPolicyCanIgnoreVolatileObjectId() {
        FingerprintPolicyProperties properties = new FingerprintPolicyProperties();
        FingerprintPolicyProperties.Policy policy = new FingerprintPolicyProperties.Policy();
        policy.setName("mes-error-code-only");
        policy.setSourceSystems(List.of("MES"));
        policy.setEventTypes(List.of("EQUIPMENT_ALARM"));
        policy.setFields(List.of("tenantId", "sourceSystem", "siteId", "plantId", "eventType", "errorCode", "maskedMessage"));
        properties.setPolicies(List.of(policy));

        FingerprintGenerator generator = new FingerprintGenerator(
                properties,
                new FingerprintPolicyResolver(properties),
                new FingerprintFieldResolver(new DynamicTokenMasker(properties)));
        EventNormalizer normalizer = new EventNormalizer();

        EventIntakeRequest a = request("Chamber temperature over threshold at 10:03:21");
        a.setObjectId("EQP-1001");
        EventIntakeRequest b = request("Chamber temperature over threshold at 10:04:59");
        b.setObjectId("EQP-9999");

        String fa = generator.generate(normalizer.normalize(a));
        String fb = generator.generate(normalizer.normalize(b));

        assertThat(fa).isEqualTo(fb);
    }

    private EventIntakeRequest request(String message) {
        EventIntakeRequest request = new EventIntakeRequest();
        request.setTenantId("tenant-a");
        request.setSourceSystem("MES");
        request.setSiteId("TNN");
        request.setPlantId("TNN-FAB-01");
        request.setObjectType("EQUIPMENT");
        request.setObjectId("EQP-1001");
        request.setEventType("EQUIPMENT_ALARM");
        request.setErrorCode("TEMP_HIGH");
        request.setSeverity("HIGH");
        request.setMessage(message);
        return request;
    }
}
