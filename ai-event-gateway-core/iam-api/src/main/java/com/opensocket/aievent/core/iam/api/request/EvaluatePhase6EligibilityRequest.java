package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;
public record EvaluatePhase6EligibilityRequest(@NotBlank @Size(max=64) String tenantId){}
