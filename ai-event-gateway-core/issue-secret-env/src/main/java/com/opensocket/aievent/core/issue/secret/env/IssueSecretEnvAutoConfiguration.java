package com.opensocket.aievent.core.issue.secret.env;

import com.opensocket.aievent.core.integration.identity.IntegrationSecretResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@AutoConfiguration
@Profile("!prod")
@ConditionalOnProperty(prefix = "integration-identity", name = "secret-resolver", havingValue = "ENV_REFERENCE")
public class IssueSecretEnvAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(IntegrationSecretResolver.class)
    EnvironmentIntegrationSecretResolver environmentIntegrationSecretResolver() {
        return new EnvironmentIntegrationSecretResolver();
    }
}
