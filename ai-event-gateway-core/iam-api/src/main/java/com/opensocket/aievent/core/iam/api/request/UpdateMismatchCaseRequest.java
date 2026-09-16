package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;import java.time.Instant;
public record UpdateMismatchCaseRequest(@NotBlank @Size(max=32) String status,@NotBlank @Size(max=16) String severity,@NotBlank @Size(max=128) String ownerId,@NotNull Instant slaDueAt,@Size(max=4000) String resolution){}
