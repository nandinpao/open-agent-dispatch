package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime; import java.util.List; import java.util.Map;
public record IntegrationPrincipal(String tenantId,String principalId,String connectionId,String principalName,IntegrationPrincipalType principalType,String ownerDepartmentId,String ownerGroupId,String trustZoneId,String externalPrincipalIdentifier,IntegrationPrincipalStatus status,IntegrationRiskLevel riskLevel,Map<String,String> permissionSummary,List<String> overPrivilegedReasons,OffsetDateTime lastPermissionProbeAt,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt) {
 public boolean blocksProductionMapping(){ return status==IntegrationPrincipalStatus.OVER_PRIVILEGED || principalType==IntegrationPrincipalType.BREAK_GLASS || status==IntegrationPrincipalStatus.REVOKED || status==IntegrationPrincipalStatus.EXPIRED || status==IntegrationPrincipalStatus.DISABLED; }
}
