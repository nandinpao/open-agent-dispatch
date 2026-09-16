package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.Max;import jakarta.validation.constraints.Min;
public record ProcessShadowPipelineRequest(@Min(1) @Max(5000) int batchSize){}
