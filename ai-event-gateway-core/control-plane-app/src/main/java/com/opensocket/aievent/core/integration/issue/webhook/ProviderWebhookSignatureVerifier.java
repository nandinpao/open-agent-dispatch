package com.opensocket.aievent.core.integration.issue.webhook;
import java.util.List; import com.opensocket.aievent.core.integration.identity.IntegrationCredentialMetadata;
public interface ProviderWebhookSignatureVerifier {
 ProviderWebhookSignatureResult verify(ProviderWebhookSigningRequest request,String suppliedSignature,List<IntegrationCredentialMetadata> candidates);
 String mode();
}
