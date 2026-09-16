package com.opensocket.aievent.core.iam.rbac.domain.entrypoint;
import java.time.Instant;import java.time.LocalDate;import java.util.List;import java.util.Optional;
public record EntryPointAuthorityRecord(String entryPointId,EntryPointType entryPointType,String applicationId,String ownerModule,
 String displayName,Optional<String> routePattern,Optional<String> httpMethod,EntryPointAuthorityState authorityState,
 Optional<String> targetPermissionCode,Optional<String> legacyAuthorityType,List<String> legacyAuthorities,String resourceType,
 String resourceResolverId,Optional<String> exemptionReason,Optional<LocalDate> migrationDeadline,String manifestRevision,
 String sourceRef,String sourceHash,Instant lastVerifiedAt,long version,boolean unknownPermission,boolean missingMapping,
 boolean missingResolver,boolean overdue,boolean activeBypass,boolean expiredBypass) {}
