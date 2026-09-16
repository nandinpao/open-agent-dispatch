package com.opensocket.aievent.core.iam.rbac.domain.entrypoint;
import java.time.Instant;import java.time.LocalDate;import java.util.Optional;
public record LegacyAuthorityMapping(String mappingId,String legacyAuthorityType,String legacyAuthorityCode,
 Optional<String> targetPermissionCode,String ownerModule,String status,Optional<LocalDate> migrationDeadline,
 String notes,Instant updatedAt,long version) {}
