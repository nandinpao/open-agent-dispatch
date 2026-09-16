package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;import java.time.Instant;
public record CreateMismatchWaiverRequest(@NotBlank @Size(min=12,max=2000) String reason,@NotNull Instant expiresAt){}
