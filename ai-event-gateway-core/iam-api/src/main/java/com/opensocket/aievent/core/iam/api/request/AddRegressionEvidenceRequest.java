package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;
public record AddRegressionEvidenceRequest(@NotBlank @Size(max=320) String testReference,@NotBlank @Pattern(regexp="PASSED|FAILED") String result,@Size(max=8000) String detailsJson){}
