package com.opensocket.aievent.core.integration.issue.webhook;
import java.util.List; import org.springframework.security.authentication.AbstractAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority;
public final class ProviderWebhookAuthenticationToken extends AbstractAuthenticationToken {
 private final ProviderWebhookMachinePrincipal principal; public ProviderWebhookAuthenticationToken(ProviderWebhookMachinePrincipal principal){super(List.of(new SimpleGrantedAuthority("ROLE_WEBHOOK_INGESTION")));this.principal=principal;setAuthenticated(true);} public Object getCredentials(){return "";} public ProviderWebhookMachinePrincipal getPrincipal(){return principal;}
}
