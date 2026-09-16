package com.opensocket.aievent.core.iam.api.response;
import com.opensocket.aievent.core.iam.rbac.domain.entrypoint.EntryPointAuthorityRecord;import java.time.*;import java.util.*;
public record EntryPointAuthorityResponse(String entryPointId,String entryPointType,String applicationId,String ownerModule,String displayName,
 String routePattern,String httpMethod,String authorityState,String targetPermissionCode,String legacyAuthorityType,List<String> legacyAuthorities,
 String resourceType,String resourceResolverId,String exemptionReason,LocalDate migrationDeadline,String manifestRevision,String sourceRef,String sourceHash,
 Instant lastVerifiedAt,long version,boolean unknownPermission,boolean missingMapping,boolean missingResolver,boolean overdue,boolean activeBypass,boolean expiredBypass){
 public static EntryPointAuthorityResponse from(EntryPointAuthorityRecord v){return new EntryPointAuthorityResponse(v.entryPointId(),v.entryPointType().name(),v.applicationId(),v.ownerModule(),v.displayName(),v.routePattern().orElse(null),v.httpMethod().orElse(null),v.authorityState().name(),v.targetPermissionCode().orElse(null),v.legacyAuthorityType().orElse(null),v.legacyAuthorities(),v.resourceType(),v.resourceResolverId(),v.exemptionReason().orElse(null),v.migrationDeadline().orElse(null),v.manifestRevision(),v.sourceRef(),v.sourceHash(),v.lastVerifiedAt(),v.version(),v.unknownPermission(),v.missingMapping(),v.missingResolver(),v.overdue(),v.activeBypass(),v.expiredBypass());}
}
