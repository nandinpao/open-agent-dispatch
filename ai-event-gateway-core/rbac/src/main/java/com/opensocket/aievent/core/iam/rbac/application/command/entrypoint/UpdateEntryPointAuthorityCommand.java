package com.opensocket.aievent.core.iam.rbac.application.command.entrypoint;
import com.opensocket.aievent.core.iam.rbac.domain.entrypoint.EntryPointAuthorityState;import java.time.Instant;import java.time.LocalDate;import java.util.List;import java.util.Optional;
public record UpdateEntryPointAuthorityCommand(String entryPointId,String ownerModule,EntryPointAuthorityState authorityState,
 Optional<String> targetPermissionCode,Optional<String> legacyAuthorityType,List<String> legacyAuthorities,String resourceType,
 String resourceResolverId,Optional<String> exemptionReason,Optional<LocalDate> migrationDeadline,long expectedVersion,
 String actorId,String correlationId,String auditReason,Instant requestedAt) {}
