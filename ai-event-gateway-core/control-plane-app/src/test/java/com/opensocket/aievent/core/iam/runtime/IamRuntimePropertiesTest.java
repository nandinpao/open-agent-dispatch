package com.opensocket.aievent.core.iam.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opensocket.aievent.core.iam.runtime.config.IamRuntimeProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class IamRuntimePropertiesTest {
    @Test
    void rejectsPartialOrUnsafeRuntimeSecrets() {
        IamRuntimeProperties properties = new IamRuntimeProperties();
        properties.setEnabled(true);
        assertThatThrownBy(properties::validateEnabled)
                .hasMessageContaining("SESSION_COOKIE_SIGNING_SECRET");

        properties.setSessionCookieSigningSecretBase64(
                Base64.getEncoder().encodeToString(new byte[32]));
        properties.setRootInitialPassword("Temporary-Root-Password-2026!");
        properties.setOneTimeSecretDeliveryDirectory("/tmp/opendispatch-iam-secrets");
        properties.setSessionCookieSameSite("None");
        properties.setSessionCookieSecure(false);
        assertThatThrownBy(properties::validateEnabled)
                .hasMessageContaining("SameSite=None");
    }
    @Test
    void activationEmailIsExplicitlyOptIn() {
        IamRuntimeProperties properties = new IamRuntimeProperties();
        assertThat(properties.isActivationEmailEnabled()).isFalse();

        properties.setEnabled(true);
        properties.setSessionCookieSigningSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setRootInitialPassword("Temporary-Root-Password-2026!");
        properties.setOneTimeSecretDeliveryDirectory("/tmp/opendispatch-iam-secrets");
        properties.setActivationDeliveryDefaultMethod("EMAIL");

        assertThatThrownBy(properties::validateEnabled)
                .hasMessageContaining("ACTIVATION_EMAIL_ENABLED");
    }

}
