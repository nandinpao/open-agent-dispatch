package com.opensocket.aievent.core.issue.secret.vault;

import com.opensocket.aievent.core.integration.identity.IntegrationSecretResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@ConditionalOnProperty(prefix = "integration-identity", name = "secret-resolver", havingValue = "VAULT")
public class IssueSecretVaultAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(IntegrationSecretResolver.class)
    VaultIntegrationSecretResolver vaultIntegrationSecretResolver(
            @Value("${integration-identity.vault.address:}") String address,
            @Value("${integration-identity.vault.namespace:}") String namespace,
            @Value("${integration-identity.vault.token-ref:}") String tokenReference,
            ObjectMapper json) {
        return new VaultIntegrationSecretResolver(address, namespace, tokenReference, json);
    }
}
