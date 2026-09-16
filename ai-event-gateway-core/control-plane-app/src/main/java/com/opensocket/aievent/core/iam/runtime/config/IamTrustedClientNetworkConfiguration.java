package com.opensocket.aievent.core.iam.runtime.config;

import com.opensocket.aievent.core.iam.runtime.machine.TrustedClientIpResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Always-on trusted client network infrastructure.
 *
 * <p>The request-context/observation layer needs a canonical client-address resolver even when
 * machine OAuth is disabled. Keeping this bean outside the conditional machine-token
 * configuration prevents the application context from depending on
 * {@code aeg.iam.machine-token.enabled}. Forwarding headers remain untrusted unless explicit
 * trusted-proxy CIDRs are configured.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IamMachineTokenProperties.class)
public class IamTrustedClientNetworkConfiguration {

    @Bean
    TrustedClientIpResolver trustedClientIpResolver(IamMachineTokenProperties properties) {
        return new TrustedClientIpResolver(properties);
    }
}
