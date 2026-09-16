package com.opensocket.aievent.core.integration.issue.webhook;
import java.security.Principal;
public record ProviderWebhookMachinePrincipal(String tenantId,String endpointId,String connectionId,String principalId,String providerType,String credentialId,String credentialVersion,String bodyDigest,String correlationId) implements Principal {public String getName(){return principalId;}}
