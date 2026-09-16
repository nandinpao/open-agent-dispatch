package com.opensocket.aievent.core.iam.rbac.application.command.entrypoint;
import java.time.Instant;import java.time.LocalDate;import java.util.Optional;
public record UpdateLegacyAuthorityMappingCommand(String mappingId,Optional<String> targetPermissionCode,String ownerModule,
 String status,Optional<LocalDate> migrationDeadline,String notes,long expectedVersion,String actorId,String correlationId,
 String auditReason,Instant requestedAt) {}
