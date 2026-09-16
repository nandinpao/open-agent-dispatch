package com.opensocket.aievent.core.integration.identity;
 import org.springframework.beans.factory.*; import org.springframework.core.env.*;
public final class ProjectMappingMetadataRuntimeGuard implements SmartInitializingSingleton {
 private final ObjectProvider<ProviderMetadataProbeGateway> gateways; private final Environment environment;
 public ProjectMappingMetadataRuntimeGuard(ObjectProvider<ProviderMetadataProbeGateway> gateways,Environment environment){this.gateways=gateways;this.environment=environment;}
 public void afterSingletonsInstantiated(){if(!environment.getProperty("issue-tracking.enabled",Boolean.class,true))return;var installed=gateways.orderedStream().toList();if(installed.isEmpty())throw new IllegalStateException("PROVIDER_METADATA_PROBE_GATEWAY_REQUIRED");if(installed.size()!=1)throw new IllegalStateException("PROVIDER_METADATA_PROBE_GATEWAY_MUST_BE_UNIQUE");if(environment.acceptsProfiles(Profiles.of("prod"))&&installed.getFirst().getClass().getSimpleName().contains("MetadataOnly"))throw new IllegalStateException("METADATA_ONLY_PROVIDER_METADATA_PROBE_FORBIDDEN_IN_PRODUCTION");}
}
