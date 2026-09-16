package com.opensocket.aievent.core.iam.api.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aeg.iam.api")
public record IamApiProperties(
        boolean enabled,
        int defaultPageSize,
        int maximumPageSize,
        Duration cursorTtl,
        String cursorSigningSecretBase64,
        boolean requireIdempotency,
        boolean requireAuditReasonForSensitiveActions) {
    public IamApiProperties {
        defaultPageSize = defaultPageSize <= 0 ? 20 : defaultPageSize;
        maximumPageSize = maximumPageSize <= 0 ? 100 : maximumPageSize;
        cursorTtl = cursorTtl == null ? Duration.ofMinutes(15) : cursorTtl;
        cursorSigningSecretBase64 = cursorSigningSecretBase64 == null ? "" : cursorSigningSecretBase64.trim();
        if (defaultPageSize > maximumPageSize || maximumPageSize > 100) {
            throw new IllegalArgumentException("IAM API page size must satisfy default <= maximum <= 100");
        }
        if (cursorTtl.isNegative() || cursorTtl.isZero() || cursorTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("IAM cursor TTL must be between 1 second and 1 hour");
        }
        if (enabled && cursorSigningSecretBase64.isBlank()) {
            throw new IllegalArgumentException("AEG_IAM_API_CURSOR_SIGNING_SECRET_BASE64 is required when IAM API is enabled");
        }
    }
}
