package com.opensocket.aievent.core.integration.issue.webhook;
import org.springframework.boot.context.properties.EnableConfigurationProperties; import org.springframework.context.annotation.*; import org.springframework.core.annotation.Order; import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; import org.springframework.security.config.http.SessionCreationPolicy; import org.springframework.security.web.SecurityFilterChain; import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import com.opensocket.aievent.core.integration.identity.*; import tools.jackson.databind.ObjectMapper;
@Configuration @EnableConfigurationProperties(ProviderWebhookSecurityProperties.class)
public class ProviderWebhookSecurityConfiguration {
 @Bean ProviderWebhookRateLimiter providerWebhookRateLimiter(){return new ProviderWebhookRateLimiter();}
 @Bean @Order(2) SecurityFilterChain providerWebhookSecurityFilterChain(HttpSecurity http,IntegrationWebhookEndpointRepository endpoints,IntegrationIdentityRepository identities,ProviderWebhookSignatureVerifier signatures,ProviderWebhookRateLimiter limiter,ProviderWebhookSecurityProperties properties,ObjectMapper json)throws Exception{
  ProviderWebhookAuthenticationFilter filter=new ProviderWebhookAuthenticationFilter(endpoints,identities,signatures,limiter,properties,json);
  return http.securityMatcher("/api/external/provider-webhooks/**").csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).addFilterBefore(filter,AnonymousAuthenticationFilter.class).authorizeHttpRequests(a->a.requestMatchers(HttpMethod.POST,"/api/external/provider-webhooks/*").authenticated().anyRequest().denyAll()).build();
 }
}
