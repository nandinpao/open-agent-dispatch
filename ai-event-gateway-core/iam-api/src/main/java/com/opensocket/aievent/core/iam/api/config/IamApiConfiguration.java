package com.opensocket.aievent.core.iam.api.config;

import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.context.IamAuthenticationContextResolver;
import com.opensocket.aievent.core.iam.api.context.SpringSecurityIamAuthenticationContextResolver;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyPort;
import com.opensocket.aievent.core.iam.api.pagination.SignedCursorCodec;
import com.opensocket.aievent.core.iam.api.pagination.IamPaginationPolicy;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.api.security.IamSecurityAdapter;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IamApiProperties.class)
@ConditionalOnProperty(prefix = "aeg.iam.api", name = "enabled", havingValue = "true")
public class IamApiConfiguration {
    @Bean @ConditionalOnMissingBean
    IamAuthenticationContextResolver iamAuthenticationContextResolver() {
        return new SpringSecurityIamAuthenticationContextResolver();
    }

    @Bean
    IamApiRequestContextFactory iamApiRequestContextFactory(IamAuthenticationContextResolver resolver, Clock clock) {
        return new IamApiRequestContextFactory(resolver, clock);
    }

    @Bean
    SignedCursorCodec signedCursorCodec(IamApiProperties properties, ObjectMapper objectMapper, Clock clock) {
        return SignedCursorCodec.fromBase64Secret(properties.cursorSigningSecretBase64(), properties.cursorTtl(), objectMapper, clock);
    }

    @Bean
    IamPaginationPolicy iamPaginationPolicy(IamApiProperties properties) {
        return new IamPaginationPolicy(properties);
    }

    @Bean
    IamPermissionGuard iamPermissionGuard(IamSecurityAdapter adapter) {
        return new IamPermissionGuard(adapter);
    }

    @Bean
    IamIdempotencyExecutor iamIdempotencyExecutor(IamIdempotencyPort port, ObjectMapper objectMapper, Clock clock) {
        return new IamIdempotencyExecutor(port, objectMapper, clock);
    }

    @Bean
    IamApiActivationValidator iamApiActivationValidator(ApplicationContext context) {
        return new IamApiActivationValidator(context);
    }
}
