package com.opensocket.aievent.core.integration.identity;
public record ProviderMetadataProbeRequest(String tenantId,String mappingId,String principalId,boolean forceRefresh,String correlationId) {}
