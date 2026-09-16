package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;import java.time.LocalDate;import java.util.List;
public record UpdateEntryPointAuthorityRequest(@NotBlank @Size(max=128) String ownerModule,@NotBlank String authorityState,
 @Size(max=160) String targetPermissionCode,@Size(max=64) String legacyAuthorityType,@NotNull List<String> legacyAuthorities,
 @NotBlank @Size(max=96) String resourceType,@NotBlank @Size(max=200) String resourceResolverId,@Size(max=1000) String exemptionReason,
 LocalDate migrationDeadline) {}
