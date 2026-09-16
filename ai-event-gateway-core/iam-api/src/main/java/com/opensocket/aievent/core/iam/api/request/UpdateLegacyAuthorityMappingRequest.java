package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;import java.time.LocalDate;
public record UpdateLegacyAuthorityMappingRequest(@Size(max=160) String targetPermissionCode,@NotBlank @Size(max=128) String ownerModule,
 @NotBlank @Size(max=32) String status,LocalDate migrationDeadline,@Size(max=1000) String notes) {}
