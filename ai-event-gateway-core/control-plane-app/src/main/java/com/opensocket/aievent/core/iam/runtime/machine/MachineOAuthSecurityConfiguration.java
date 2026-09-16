package com.opensocket.aievent.core.iam.runtime.machine;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.opensocket.aievent.core.iam.rbac.application.port.out.TenantRbacExecutionPort;
import com.opensocket.aievent.core.iam.runtime.config.IamMachineTokenProperties;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineCredentialDirectoryPort;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineSigningKeyRepository;
import com.opensocket.aievent.core.iam.token.application.port.out.ServiceAccountRepository;
import com.opensocket.aievent.core.iam.token.application.service.MachineJwtApplicationService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 12.1: Spring Security 7 owns the OAuth Authorization Server protocol.
 * OpenDispatch remains the authority for machine principals, credentials, RBAC and JWT claims.
 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="aeg.iam.machine-token",name="enabled",havingValue="true")
public class MachineOAuthSecurityConfiguration {

    @Bean
    RegisteredClientRepository openDispatchRegisteredClientRepository(MachineCredentialDirectoryPort directory,
            TenantRbacExecutionPort tenants,ServiceAccountRepository accounts,IamMachineTokenProperties properties){
        return new OpenDispatchRegisteredClientRepository(directory,tenants,accounts,properties);
    }

    @Bean
    JWKSource<SecurityContext> openDispatchMachineJwkSource(MachineSigningKeyRepository keys,
            MachineJwtApplicationService jwt,Clock clock,PlatformTransactionManager transactionManager){
        TransactionTemplate transactions=new TransactionTemplate(transactionManager);
        transactions.setName("iam-machine:jwks-signing-key");
        return new OpenDispatchMachineJwkSource(keys,jwt,clock,transactions);
    }

    @Bean
    AuthorizationServerSettings openDispatchAuthorizationServerSettings(IamMachineTokenProperties properties){
        return AuthorizationServerSettings.builder()
                .issuer(properties.getIssuer())
                .tokenEndpoint("/oauth2/token")
                .jwkSetEndpoint("/oauth2/jwks")
                .tokenRevocationEndpoint("/oauth2/revoke")
                .tokenIntrospectionEndpoint("/oauth2/introspect")
                .build();
    }

    @Bean @Order(2)
    SecurityFilterChain machineOAuthAuthorizationServerSecurityFilterChain(HttpSecurity http,
            RegisteredClientRepository clients,IamMachineTokenRuntimeOrchestrator orchestrator,
            IamMachineTokenProperties properties,TrustedClientIpResolver clientIp,
            AuthorizationServerSettings settings) throws Exception {
        OpenDispatchMachineClientAuthenticationProvider clientProvider=
                new OpenDispatchMachineClientAuthenticationProvider(orchestrator,clients);
        OpenDispatchClientCredentialsAuthenticationProvider grantProvider=
                new OpenDispatchClientCredentialsAuthenticationProvider(orchestrator,properties);
        OpenDispatchClientSecretBasicAuthenticationConverter clientConverter=
                new OpenDispatchClientSecretBasicAuthenticationConverter(clientIp);

        // Phase 12.1 deliberately exposes only the protocol endpoints that are backed by
        // OpenDispatch canonical authority. Standard revocation/introspection are not exposed
        // until their semantics are wired to SecurityEpoch / credential authority instead of an
        // unrelated framework authorization store.
        http.securityMatcher("/oauth2/token","/oauth2/jwks");
        http.oauth2AuthorizationServer(authorizationServer -> authorizationServer
                .authorizationServerSettings(settings)
                .registeredClientRepository(clients)
                .clientAuthentication(client -> client
                        .authenticationConverters(converters -> converters.add(0,clientConverter))
                        .authenticationProviders(providers -> providers.add(0,clientProvider)))
                .tokenEndpoint(token -> token.authenticationProviders(providers -> providers.add(0,grantProvider))));
        http.csrf(csrf->csrf.ignoringRequestMatchers("/oauth2/token"));
        http.sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.requestCache(AbstractHttpConfigurer::disable);
        // HF06: canonical MVC protocol endpoints are a fail-closed fallback when the
        // Spring Authorization Server endpoint filters are not materialized by the
        // active framework/auto-configuration combination. Both paths delegate to
        // the same OpenDispatch machine authority. Allow the protocol paths through
        // the chain so the MVC fallback is reachable instead of being converted to 401.
        http.authorizeHttpRequests(a->a
                .requestMatchers("/oauth2/jwks","/oauth2/token").permitAll()
                .anyRequest().denyAll());
        return http.build();
    }

    /** Opt-in transition chain for the old /oauth/token and /.well-known/jwks.json aliases. */
    @Bean @Order(3)
    @ConditionalOnProperty(prefix="aeg.iam.machine-token",name="legacy-token-endpoint-enabled",havingValue="true")
    SecurityFilterChain legacyMachineOAuthCompatibilitySecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/oauth/token","/.well-known/jwks.json")
                .csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a->a.anyRequest().permitAll())
                .build();
    }
}
