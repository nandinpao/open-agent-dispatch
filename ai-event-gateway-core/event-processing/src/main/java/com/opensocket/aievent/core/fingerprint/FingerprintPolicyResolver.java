package com.opensocket.aievent.core.fingerprint;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.event.NormalizedEvent;

@Component
public class FingerprintPolicyResolver {
    private final FingerprintRuntimeConfigurationView runtimeConfiguration;

    @Autowired
    public FingerprintPolicyResolver(FingerprintRuntimeConfigurationView runtimeConfiguration) {
        this.runtimeConfiguration = runtimeConfiguration;
    }

    public FingerprintPolicyResolver(FingerprintPolicyProperties properties) {
        this(new FingerprintRuntimeConfigurationView(properties));
    }

    public FingerprintPolicyResolver() {
        this(new FingerprintRuntimeConfigurationView(new FingerprintPolicyProperties()));
    }

    public FingerprintTemplate resolve(NormalizedEvent event) {
        if (runtimeConfiguration.enabled() && runtimeConfiguration.policies() != null) {
            for (FingerprintPolicyProperties.Policy policy : runtimeConfiguration.policies()) {
                if (matches(policy, event)) {
                    List<String> fields = policy.getFields() == null || policy.getFields().isEmpty()
                            ? runtimeConfiguration.defaultFields()
                            : policy.getFields();
                    return new FingerprintTemplate(policy.getName(), fields);
                }
            }
        }
        return new FingerprintTemplate("default", runtimeConfiguration.defaultFields());
    }

    private boolean matches(FingerprintPolicyProperties.Policy policy, NormalizedEvent event) {
        return matchesOne(policy.getSourceSystems(), event.sourceSystem())
                && matchesOne(policy.getEventTypes(), event.eventType())
                && matchesOne(policy.getObjectTypes(), event.objectType())
                && matchesOne(policy.getErrorCodes(), event.errorCode());
    }

    private boolean matchesOne(List<String> expectedValues, String actualValue) {
        if (expectedValues == null || expectedValues.isEmpty()) {
            return true;
        }
        String actual = normalize(actualValue);
        return expectedValues.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .anyMatch(expected -> "*".equals(expected) || expected.equals(actual));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
    }
}
