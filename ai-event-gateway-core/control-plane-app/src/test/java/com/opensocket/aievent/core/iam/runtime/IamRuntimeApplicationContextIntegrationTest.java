package com.opensocket.aievent.core.iam.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.iam.runtime.config.IamRuntimeConfiguration;
import com.opensocket.aievent.core.iam.runtime.security.IamSessionCookieCodec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IamRuntimeApplicationContextIntegrationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(IamRuntimeConfiguration.class);

    @Test
    void disabledRuntimeDoesNotRegisterExecutableIamBeans() {
        runner.withPropertyValues("aeg.iam.runtime.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(IamSessionCookieCodec.class));
    }

    @Test
    void enabledButPartialCompositionFailsClosed() {
        runner.withPropertyValues(
                        "aeg.iam.runtime.enabled=true",
                        "aeg.iam.api.enabled=true",
                        "aeg.iam.authentication.enabled=true",
                        "aeg.iam.rbac.enabled=true",
                        "aeg.iam.token.enabled=true",
                        "aeg.iam.runtime.session-cookie-signing-secret-base64=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
                        "aeg.iam.runtime.bootstrap-secret=bootstrap",
                        "aeg.iam.runtime.one-time-secret-delivery-directory=/tmp/opendispatch-iam-test")
                .run(context -> assertThat(context).hasFailed());
    }
}
