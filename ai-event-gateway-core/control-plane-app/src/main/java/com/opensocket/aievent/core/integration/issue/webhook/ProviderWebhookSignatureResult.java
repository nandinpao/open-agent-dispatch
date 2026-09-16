package com.opensocket.aievent.core.integration.issue.webhook;
public record ProviderWebhookSignatureResult(boolean verified,String credentialId,String secretVersion,String reasonCode) {
 public static ProviderWebhookSignatureResult accepted(String credentialId,String version){return new ProviderWebhookSignatureResult(true,credentialId,version,null);} public static ProviderWebhookSignatureResult rejected(String reason){return new ProviderWebhookSignatureResult(false,null,null,reason);}
}
