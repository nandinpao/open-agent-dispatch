package com.opensocket.aievent.core.iam.api.security;

import com.opensocket.aievent.core.iam.rbac.application.port.in.AuthorizationPort;
import com.opensocket.aievent.core.iam.rbac.application.port.in.ShadowAuthorizationPort;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix = "aeg.iam.api", name = "enabled", havingValue = "true")
public class IamSecurityAdapterConfiguration {
    @Bean
    IamSecurityAdapter iamSecurityAdapter(AuthorizationPort authorization,
                                          ObjectProvider<ShadowAuthorizationPort> shadow,
                                          Clock clock) {
        return new IamSecurityAdapter(authorization, shadow.getIfAvailable(), clock);
    }
}
