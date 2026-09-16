package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime; import java.util.List; import java.util.Map;
public record PermissionProbeResult(String tenantId,String probeId,String connectionId,String principalId,String mappingId,ProjectMappingStatus overallStatus,Map<IntegrationPermissionCapability,PermissionProbeResultStatus> capabilityResults,List<String> elevatedPermissions,String providerResponseSummary,OffsetDateTime startedAt,OffsetDateTime completedAt,String correlationId) {}
