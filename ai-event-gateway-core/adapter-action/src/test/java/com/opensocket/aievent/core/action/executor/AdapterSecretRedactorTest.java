package com.opensocket.aievent.core.action.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AdapterSecretRedactorTest {

    @Test
    void shouldRedactSensitivePayloadKeysRecursively() {
        Map<String, Object> redacted = AdapterSecretRedactor.redactMap(Map.of(
                "apiKey", "redmine-secret-token",
                "private_token", "gitlab-secret-token",
                "nested", Map.of("authorization", "Bearer hidden-token", "safe", "visible"),
                "items", List.of(Map.of("clientSecret", "client-secret-123"))));

        assertThat(redacted.get("apiKey")).isEqualTo(AdapterSecretRedactor.REDACTED);
        assertThat(redacted.get("private_token")).isEqualTo(AdapterSecretRedactor.REDACTED);
        assertThat(redacted.toString()).doesNotContain("redmine-secret-token", "gitlab-secret-token", "hidden-token", "client-secret-123");
        assertThat(redacted.toString()).contains("visible");
    }

    @Test
    void shouldRedactSecretsFromTextAndUrls() {
        String redacted = AdapterSecretRedactor.redactText("Authorization: Bearer abc123 PRIVATE-TOKEN=xyz https://user:pass@example.com/path?access_token=secret-value");

        assertThat(redacted).contains(AdapterSecretRedactor.REDACTED);
        assertThat(redacted).doesNotContain("abc123", "xyz", "user:pass", "secret-value");
    }

    @Test
    void shouldClampUnsafeHttpTimeouts() {
        assertThat(AdapterSecretRedactor.safeHttpTimeout(null, Duration.ofSeconds(30))).isEqualTo(Duration.ofSeconds(30));
        assertThat(AdapterSecretRedactor.safeHttpTimeout(Duration.ZERO, Duration.ofSeconds(30))).isEqualTo(Duration.ofSeconds(30));
        assertThat(AdapterSecretRedactor.safeHttpTimeout(Duration.ofMillis(10), Duration.ofSeconds(30))).isEqualTo(Duration.ofSeconds(1));
        assertThat(AdapterSecretRedactor.safeHttpTimeout(Duration.ofMinutes(10), Duration.ofSeconds(30))).isEqualTo(Duration.ofMinutes(5));
    }
}
