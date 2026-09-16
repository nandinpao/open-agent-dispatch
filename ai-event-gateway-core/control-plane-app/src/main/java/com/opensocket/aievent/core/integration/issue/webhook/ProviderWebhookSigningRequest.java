package com.opensocket.aievent.core.integration.issue.webhook;
public record ProviderWebhookSigningRequest(String method,String canonicalPath,String connectionId,String providerType,String providerEventId,String timestamp,String nonce,String bodyDigest) {
 public String signingInput(){return String.join("\n",safe(method),safe(canonicalPath),safe(connectionId),safe(providerType),safe(providerEventId),safe(timestamp),safe(nonce),safe(bodyDigest));}
 private static String safe(String v){return v==null?"":v.trim();}
}
