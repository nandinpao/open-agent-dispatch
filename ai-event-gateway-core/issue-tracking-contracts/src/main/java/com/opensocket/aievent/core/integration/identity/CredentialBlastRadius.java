package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime; import java.util.List;
public record CredentialBlastRadius(String credentialId,String principalId,String connectionId,String ownerDepartmentId,String trustZoneId,List<String> mappedProjectIds,long pendingSyncJobs,OffsetDateTime expiresAt,OffsetDateTime lastUsedAt) {}
