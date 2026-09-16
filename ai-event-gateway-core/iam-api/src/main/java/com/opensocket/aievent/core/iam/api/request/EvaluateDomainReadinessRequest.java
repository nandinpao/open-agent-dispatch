package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;import java.time.Instant;
public record EvaluateDomainReadinessRequest(@NotBlank @Size(max=64) String tenantId,@NotBlank @Pattern(regexp="TASK|A2A|AGENT|ISSUE|INTEGRATION") String domainCode,@NotNull Instant windowStartedAt,@NotNull Instant windowEndedAt){}
