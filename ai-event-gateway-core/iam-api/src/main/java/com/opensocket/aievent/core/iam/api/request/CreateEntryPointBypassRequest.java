package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;import java.time.Instant;
public record CreateEntryPointBypassRequest(@NotBlank @Size(max=240) String entryPointId,@NotBlank @Size(max=128) String ownerId,
 @NotBlank @Size(min=12,max=1000) String reason,@NotBlank @Size(min=3,max=500) String replacement,@NotNull Instant expiresAt) {}
