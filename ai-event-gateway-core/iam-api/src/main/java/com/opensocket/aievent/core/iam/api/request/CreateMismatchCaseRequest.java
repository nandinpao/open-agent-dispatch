package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;import java.time.Instant;
public record CreateMismatchCaseRequest(@NotBlank @Size(max=64) String tenantId,@NotBlank @Size(max=128) String comparisonId,@Size(max=220) String entryPointId,@NotBlank @Size(max=128) String ownerId,@NotNull Instant slaDueAt,@NotBlank @Size(min=8,max=240) String title){}
